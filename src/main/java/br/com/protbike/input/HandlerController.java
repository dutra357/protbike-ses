package br.com.protbike.input;

import br.com.protbike.config.DlqPublisher;
import br.com.protbike.exceptions.taxonomy.ResultadoEnvio;
import br.com.protbike.metrics.Metricas;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.BoletoNotificacaoWrapper;
import br.com.protbike.service.ProcessadorNotificacao;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
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
public class HandlerController implements RequestHandler<SQSEvent, Void> {

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
    public Void handleRequest(SQSEvent event, Context context) {

        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        metricas.reset();

        try {
            for (SQSMessage sqsMessage : event.getRecords()) {
                metricas.mensagensSqsTotal++;
                List<BoletoNotificacaoMessage> paraDlq = new ArrayList<>();

                try {
                    BoletoNotificacaoWrapper wrapper = objectMapper.readValue(
                            sqsMessage.getBody(),
                            BoletoNotificacaoWrapper.class
                    );

                    if (wrapper.boletos() == null || wrapper.boletos().isEmpty()) {
                        LOG.warnf("Mensagem SQS vazia. sqsId=%s awsRequestId=%s",
                                sqsMessage.getMessageId(),
                                context.getAwsRequestId());
                        continue;
                    }

                    String tenantId = wrapper.boletos().get(0).tenantId();
                    String processamentoId = wrapper.boletos().get(0).processamentoId();

                    LOG.infof(
                            "evento=inicio_processamento tenant=%s processamento=%s boletos=%d awsRequestId=%s sqsId=%s",
                            tenantId,
                            processamentoId,
                            wrapper.boletos().size(),
                            context.getAwsRequestId(),
                            sqsMessage.getMessageId()
                    );

                    for (BoletoNotificacaoMessage boleto : wrapper.boletos()) {
                        List<ResultadoEnvio> resultados = processador.processarEntrega(boleto);

                        for (ResultadoEnvio resultado : resultados) {
                            if (resultado.sucesso()) {
                                metricas.enviosSucesso++;
                            }
                            else if (resultado.retryavel()) {
                                throw new RuntimeException("Erro retryável: " + resultado.motivo());
                            }
                            else {
                                metricas.enviosFalha++;
                                paraDlq.add(boleto);
                            }
                        }

                        metricas.boletosTotal++;
                    }

                    if (!paraDlq.isEmpty()) {
                        try {
                            dlqPublisher.enviar(paraDlq);
                        } catch (Exception e) {
                            throw new RuntimeException("Falha ao publicar na DLQ", e);
                        }
                    }

                } catch (Exception e) {
                    LOG.errorf(e,
                            "evento=erro_lote sqsId=%s awsRequestId=%s",
                            sqsMessage.getMessageId(),
                            context.getAwsRequestId()
                    );

                    // ESSENCIAL: faz o SQS reenfileirar
                    throw e;
                }

                LOG.infof(
                        "[FIM] Lote: boletosTotal=%d enviosSucesso=%d enviosFalha=%d sqsId=%s awsRequestId=%s",
                        metricas.boletosTotal,
                        metricas.enviosSucesso,
                        metricas.enviosFalha,
                        sqsMessage.getMessageId(),
                        context.getAwsRequestId()
                );
            }

        } finally {
            requestContext.terminate();
        }
        return null;
    }
}