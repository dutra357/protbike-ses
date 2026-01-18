package br.com.protbike.strategy;

import br.com.protbike.records.BoletoNotificacaoMessage;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.ses.SesClient;

@ApplicationScoped
public class EmailStrategy implements NotificationStrategy {

    private static final Logger LOG = Logger.getLogger(EmailStrategy.class.getName());


    private final SesClient sesClient;

    public EmailStrategy(SesClient sesClient) {
        this.sesClient = sesClient;
    }

    @Override
    public String getChannelName() {
        return "EMAIL";
    }

    @Override
    public void send(BoletoNotificacaoMessage message) {
        LOG.infof("Enviando Email para %s via SES", message.destinatario().email());

        // Exemplo simplificado de chamada ao SES
        // Na prática, você montaria o HTML baseado no objeto Boleto
        /*

        sesClient.sendEmail(SendEmailRequest.builder()
                .source("nao-responda@protbike.com.br")
                .destination(d -> d.toAddresses(message.destinatario().email()))
                .message(m -> m.subject(s -> s.data("Seu Boleto Chegou"))
                               .body(b -> b.text(t -> t.data("Link: " + message.boleto().linkBoleto()))))
                .build());
        */
    }
}
