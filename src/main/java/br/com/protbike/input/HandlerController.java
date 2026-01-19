package br.com.protbike.input;

import br.com.protbike.metrics.EmfLogger;
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

@Named("processador-boletos-ses")
public class HandlerController implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOG = Logger.getLogger(HandlerController.class);

    private final ObjectMapper objectMapper;
    private final ProcessadorNotificacao processador;
    private final Metricas metricas;

//    private final EmfLogger emfLogger;


    public HandlerController(ObjectMapper objectMapper, ProcessadorNotificacao processador, Metricas metricas) {
        this.objectMapper = objectMapper;
        this.processador = processador;
        this.metricas = metricas;
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {

        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();

        for (SQSMessage sqsMessage : event.getRecords()) {
            metricas.mensagensSqsTotal++;

            try {
                BoletoNotificacaoWrapper wrapper = objectMapper.readValue(
                        sqsMessage.getBody(),
                        BoletoNotificacaoWrapper.class
                );

                // Validação
                if (wrapper.boletos() != null && !wrapper.boletos().isEmpty()) {

                    String tenantId = wrapper.boletos().get(0).tenantId();
                    String processamentoId = wrapper.boletos().get(0).processamentoId();

                    LOG.infof(
                            "[INICIO] Processando batch. tenantID=%s processamentoID=%s boletos=%d AwsRequestId=%s",
                            tenantId,
                            processamentoId,
                            wrapper.boletos().size(),
                            context.getAwsRequestId()
                    );

                    for (BoletoNotificacaoMessage boletoNotificacaoMessage : wrapper.boletos()) {
                        processador.processarEntrega(boletoNotificacaoMessage);
                        metricas.boletosTotal++;
                    }

                } else {
                    LOG.warnf("Mensagem com lista de boletos vazia ou nula. Ausente parse JSON. ContextoLambdaID={}",
                            context.getAwsRequestId());
                }

            } catch (Exception e) {
                LOG.error("ERRO processando do lote. SqsID: " + sqsMessage.getMessageId(), e);

            } finally {
                requestContext.terminate();
            }

            LOG.infof(
                    "[FIM] Execução concluída. totalBoletos=%d sucesso=%d falha=%d enviosOk=%d enviosFalha=%d",
                    metricas.boletosTotal,
                    metricas.boletosSucesso,
                    metricas.boletosFalha,
                    metricas.enviosSucesso,
                    metricas.enviosFalha
            );

//            emfLogger.logMetricas(Map.of(
//                    "boletos_total", metricas.boletosTotal,
//                    "boletos_sucesso", metricas.boletosSucesso,
//                    "boletos_falha", metricas.boletosFalha,
//                    "envios_sucesso", metricas.enviosSucesso,
//                    "envios_falha", metricas.enviosFalha
//            ));
        }

        return null;
    }
}