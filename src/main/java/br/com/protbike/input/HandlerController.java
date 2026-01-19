package br.com.protbike.input;

import br.com.protbike.config.DlqPublisher;
import br.com.protbike.exceptions.taxonomy.ResultadoEnvio;
import br.com.protbike.metrics.Metricas;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.BoletoNotificacaoWrapper;
import br.com.protbike.service.ProcessadorNotificacao;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

@Named("processador-boletos-ses")
public class HandlerController implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final Logger LOG = Logger.getLogger(HandlerController.class);

    private final ObjectMapper objectMapper;
    private final ProcessadorNotificacao processador;
    private final Metricas metricas;
    private final DlqPublisher dlqPublisher;

    public HandlerController(ObjectMapper objectMapper,
                             ProcessadorNotificacao processador,
                             Metricas metricas,
                             DlqPublisher dlqPublisher) {
        this.objectMapper = objectMapper;
        this.processador = processador;
        this.metricas = metricas;
        this.dlqPublisher = dlqPublisher;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {

        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();

        // Lista para o SQS saber quais mensagens do lote (envelope) falharam totalmente
        List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();

        try {
            for (SQSMessage sqsMessage : event.getRecords()) {
                try {
                    processarMensagemSqs(sqsMessage, context);

                } catch (Exception e) {
                    LOG.errorf(e, "Erro fatal no processamento da mensagem SQS %s", sqsMessage.getMessageId());
                    batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(sqsMessage.getMessageId()));
                }
            }
        } finally {
            requestContext.terminate();
        }
        return new SQSBatchResponse(batchItemFailures);
    }

    private void processarMensagemSqs(SQSMessage sqsMessage, Context context) throws Exception {
        metricas.reset();

        BoletoNotificacaoWrapper wrapper = objectMapper.readValue(
                sqsMessage.getBody(),
                BoletoNotificacaoWrapper.class
        );

        if (wrapper.boletos() == null || wrapper.boletos().isEmpty()) {
            return;
        }

        List<BoletoNotificacaoMessage> falhasParaDlq = new ArrayList<>();

        for (BoletoNotificacaoMessage boleto : wrapper.boletos()) {
            try {
                boolean sucessoBoleto = executarEntregaBoleto(boleto);

                if (!sucessoBoleto) {
                    falhasParaDlq.add(boleto);
                }

            } catch (Exception e) {
                LOG.errorf(e, "Erro inesperado no boleto %s, enviando para lista de falhas", boleto.numeroProtocolo());
                falhasParaDlq.add(boleto);
            }
        }

        if (!falhasParaDlq.isEmpty()) {
            try {
                dlqPublisher.enviar(falhasParaDlq);
                LOG.warnf("Lote processado com %d falhas enviadas para DLQ. sqsId=%s",
                        falhasParaDlq.size(), sqsMessage.getMessageId());

            } catch (Exception e) {
                // Se falhar o envio para a DLQ, lançamos exceção para o SQS não apagar a mensagem original
                throw new RuntimeException("Falha crítica ao publicar na DLQ", e);
            }
        }
    }

    private boolean executarEntregaBoleto(BoletoNotificacaoMessage boleto) {
        List<ResultadoEnvio> resultados = processador.processarEntrega(boleto);

        boolean tudoSucesso = true;

        for (ResultadoEnvio resultado : resultados) {
            if (resultado.sucesso()) {
                metricas.enviosSucesso++;

            } else if (resultado.retryavel()) {
                // Se for algo temporário (ex: API do SES fora), lançamos exceção
                // Isso fará a MENSAGEM SQS INTEIRA voltar para a fila.
                throw new RuntimeException("Erro temporário (Retry): " + resultado.motivo());
            } else {
                metricas.enviosFalha++;
                tudoSucesso = false;
            }
        }
        metricas.boletosTotal++;
        return tudoSucesso;
    }
}