package br.com.protbike.input;

import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.BoletoNotificacaoWrapper;
import br.com.protbike.service.ProcessadorNotificacao;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

@Named("processador-boletos-ses")
public class HandlerController implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOG = Logger.getLogger(HandlerController.class);

    private final ObjectMapper objectMapper;
    private final ProcessadorNotificacao processador;

    public HandlerController(ObjectMapper objectMapper, ProcessadorNotificacao processador) {
        this.objectMapper = objectMapper;
        this.processador = processador;
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {

        for (SQSMessage sqsMessage : event.getRecords()) {
            try {

                BoletoNotificacaoWrapper wrapper = objectMapper.readValue(
                        sqsMessage.getBody(),
                        BoletoNotificacaoWrapper.class
                );

                // Validação
                if (wrapper.boletos() != null && !wrapper.boletos().isEmpty()) {

                    LOG.infof("Processando lista interna de %d boletos", wrapper.boletos().size());

                    for (BoletoNotificacaoMessage boletoNotificacaoMessage : wrapper.boletos()) {
                        processador.processarEntrega(boletoNotificacaoMessage);
                    }

                } else {
                    LOG.warn("Mensagem com lista de boletos vazia ou nula. Ausente parse JSON.");
                }

            } catch (Exception e) {
                // Aqui reside uma decisão importante de arquitetura:
                // Se falhar UM boleto dentro da lista, você quer falhar a mensagem SQS inteira?
                // Se sim, lance a exceção. A mensagem voltará para a fila e será processada novamente.
                LOG.error("Erro processando mensagem SQS ID: " + sqsMessage.getMessageId(), e);
                throw new RuntimeException("Falha no processamento do batch", e);
            }
        }
        return null;
    }
}