package br.com.protbike.strategy;

import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.EmailJob;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;


import static br.com.protbike.utils.BoletoEmailFormatter.formatarAssunto;
import static br.com.protbike.utils.BoletoEmailFormatter.formatarCorpoEmail;

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
    public void send(BoletoNotificacaoMessage boletoNotificacaoMessage) {

        LOG.infof("Enviando Email para %s via SES", boletoNotificacaoMessage.destinatario().email());

        String assunto = formatarAssunto(boletoNotificacaoMessage);
        String corpoEmail = formatarCorpoEmail(boletoNotificacaoMessage);

        EmailJob email = new EmailJob(
                boletoNotificacaoMessage.destinatario().email(),
                "contato@protbike.com.br",
                assunto,
                "",
                corpoEmail
        );

        sesClient.sendEmail(SendEmailRequest.builder()
                .source("contato@protbike.com.br")
                .destination(d -> d.toAddresses(boletoNotificacaoMessage.destinatario().email()))
                .message(email)
                .build());

    }
}
