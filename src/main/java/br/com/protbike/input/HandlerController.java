package br.com.protbike.input;

import br.com.protbike.service.DlqPublisher;
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
import java.util.Optional;

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
                MDC.clear();
                MDC.put("aws_request_id", context.getAwsRequestId());
                MDC.put("sqs_message_id", sqsMessage.getMessageId());

                try {
                    processarMensagemSqs(sqsMessage);

                } catch (Exception e) {
                    LOG.errorf(e, "Erro fatal no processamento da mensagem SQS. Lote não processado.");
                    // Reporta falha individual para o SQS (Partial Batch Response)
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

        // PROTEÇÃO CONTRA NPE: Verificação rigorosa do wrapper
        if (wrapper == null || wrapper.boletos() == null || wrapper.boletos().isEmpty()) {
            LOG.warn("Lote recebido vazio ou inválido.");
            return;
        }

        // NÍVEL 2 MDC: Contexto de Negócio com Proteção contra Null
        configurarMdcNegocio(wrapper);

        List<BoletoNotificacaoMessage> falhasParaDlq = new ArrayList<>();

        for (BoletoNotificacaoMessage boleto : wrapper.boletos()) {
            try {
                MDC.put("protocolo_bol", Optional.ofNullable(boleto.numeroProtocolo()).orElse("N/A"));

                boolean sucessoBoleto = executarEntregaBoleto(boleto);

                if (!sucessoBoleto) {
                    falhasParaDlq.add(boleto);
                }

            } catch (Exception e) {
                LOG.errorf(e, "Erro inesperado ao processar boleto unitário.");
                falhasParaDlq.add(boleto);
            } finally {
                MDC.remove("protocolo_bol");
            }
        }

        if (!falhasParaDlq.isEmpty()) {
            dlqPublisher.enviar(falhasParaDlq);
            LOG.warnf("Lote concluído com %d falhas enviadas para DLQ customizada.", falhasParaDlq.size());
        } else {
            LOG.infof("Lote processado com sucesso total. qtd=%d", wrapper.boletos().size());
        }
    }

    private void configurarMdcNegocio(BoletoNotificacaoWrapper wrapper) {
        BoletoNotificacaoMessage ref = wrapper.boletos().get(0);
        Optional.ofNullable(ref.meta().tenantId()).ifPresent(v -> MDC.put("tenant_id", v));
        Optional.ofNullable(ref.meta().csvProcessamentoId()).ifPresent(v -> MDC.put("csv_processamento_id", v));
        Optional.ofNullable(wrapper.loteProcessamentoId()).ifPresent(v -> MDC.put("lote_id", v));
        Optional.ofNullable(ref.meta().associacaoApelido()).ifPresent(v -> MDC.put("associacao_apelido", v));
        Optional.ofNullable(ref.meta().origemCsv()).ifPresent(v -> MDC.put("origem_csv", v));
    }

    private boolean executarEntregaBoleto(BoletoNotificacaoMessage boleto) {
        List<ResultadoEnvio> resultados = processador.processarEntrega(boleto);

        boolean tudoSucesso = true;

        for (ResultadoEnvio resultado : resultados) {
            if (resultado.sucesso()) {
                metricas.enviosSucesso++;
            } else if (resultado.retryavel()) {
                // Se um dos canais for retryável (ex: SES fora), lançamos para o SQS reprocessar o lote
                throw new RuntimeException("Erro temporário SES (Retry): " + resultado.motivo());
            } else {
                metricas.enviosFalha++;
                tudoSucesso = false;
            }
        }
        metricas.boletosTotal++;
        return tudoSucesso;
    }
}