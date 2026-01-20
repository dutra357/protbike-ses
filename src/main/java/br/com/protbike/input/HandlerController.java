package br.com.protbike.input;

import br.com.protbike.config.DlqPublisher;
import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
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
import org.jboss.logging.MDC;

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

        List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();

        try {
            for (SQSMessage sqsMessage : event.getRecords()) {
                // NÍVEL 1 MDC: Contexto de Infraestrutura (Seguro para qualquer erro)
                MDC.clear();
                MDC.put("aws_request_id", context.getAwsRequestId());
                MDC.put("sqs_message_id", sqsMessage.getMessageId());

                try {
                    processarMensagemSqs(sqsMessage);

                } catch (Exception e) {
                    // LOG sai com aws_request_id e sqs_message_id garantidos
                    LOG.errorf(e, "Erro fatal no processamento da mensagem SQS");
                    batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(sqsMessage.getMessageId()));
                }
            }
        } finally {
            MDC.clear();
            requestContext.terminate();
        }
        return new SQSBatchResponse(batchItemFailures);
    }

    private void processarMensagemSqs(SQSMessage sqsMessage) throws Exception {
        metricas.reset();

        BoletoNotificacaoWrapper wrapper = objectMapper.readValue(
                sqsMessage.getBody(),
                BoletoNotificacaoWrapper.class
        );

        if (wrapper.boletos() == null || wrapper.boletos().isEmpty()) {
            LOG.warn("Recebido wrapper vazio ou sem boletos");
            return;
        }

        // NÍVEL 2 MDC: Contexto de Negócio (Lote)
        String tenantId = wrapper.boletos().get(0).tenantId();
        String processamentoId = wrapper.boletos().get(0).processamentoId();

        MDC.put("tenant_id", tenantId);
        MDC.put("processamento_id", processamentoId);

        List<BoletoNotificacaoMessage> falhasParaDlq = new ArrayList<>();

        // Processamento Item a Item
        for (BoletoNotificacaoMessage boleto : wrapper.boletos()) {
            try {
                // NÍVEL 3: Contexto do Item
                MDC.put("protocolo_bol", boleto.numeroProtocolo());

                boolean sucessoBoleto = executarEntregaBoleto(boleto);

                if (!sucessoBoleto) {
                    falhasParaDlq.add(boleto);
                }

            } catch (Exception e) {
                LOG.errorf(e, "Erro inesperado ao processar boleto");
                falhasParaDlq.add(boleto);
            } finally {
                // CRÍTICO: Remover o protocolo ao fim do loop.
                // Assim, o próximo boleto não herda protocolo errado e
                // os logs fora do loop (DLQ) não ficam com protocolo "fantasma".
                MDC.remove("protocolo_bol");
            }
        }

        // Aqui o MDC ainda tem tenant_id e processamento_id, mas NÃO tem protocolo.
        if (!falhasParaDlq.isEmpty()) {
            try {
                dlqPublisher.enviar(falhasParaDlq);
                LOG.warnf("Lote concluído com falhas parciais. total_falhas=%d", falhasParaDlq.size());
            } catch (Exception e) {
                throw new RuntimeException("Falha crítica ao publicar na DLQ", e);
            }
        } else {
            LOG.infof("Lote processado com sucesso total. qtd_boletos=%d", wrapper.boletos().size());
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