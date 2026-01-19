package br.com.protbike.strategy;

import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
import br.com.protbike.utils.BoletoEmailFormatter;
import br.com.protbike.utils.EmailFormatterHTML;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;


@ApplicationScoped
public class EmailStrategy implements NotificacaoStrategy {

    private static final Logger LOG = Logger.getLogger(EmailStrategy.class.getName());
    private final SesV2Client sesClient;

    public EmailStrategy(SesV2Client sesClient) {
        this.sesClient = sesClient;
    }

    @Override
    public CanalEntrega pegarCanal() {
        return CanalEntrega.EMAIL;
    }

    @Override
    public void enviarMensagem(BoletoNotificacaoMessage notificacaoMsg) {

        LOG.debugf("Enviando Email para %s via SES", notificacaoMsg.destinatario().email());

          SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(notificacaoMsg.meta().associacaoApelido() + " <" + notificacaoMsg.meta().admEmail() + ">")
                .replyToAddresses(notificacaoMsg.meta().admEmail())
                .destination(Destination.builder()
                        .toAddresses(notificacaoMsg.destinatario().email())
                        .build())
                .content(EmailContent.builder()
                        .simple(messageToHtml(notificacaoMsg))
                        .build())
                .build();

        SendEmailResponse response = sesClient.sendEmail(request);

        LOG.debugf("Email aceito pelo SES para envio. messageId=%s", response.messageId());
    }


    private Message messageToHtml(BoletoNotificacaoMessage msg) {

        String subject = msg.meta().associacaoApelido().toUpperCase() + " | Boleto disponível - " + msg.boleto().mesReferente();
        String htmlBody = EmailFormatterHTML.toHtml(msg);
        String textBody = BoletoEmailFormatter.formatarCorpoEmail(msg);

        return Message.builder()
                .subject(Content.builder()
                        .data(subject)
                        .charset("UTF-8")
                        .build())
                .body(Body.builder()
                        .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                        .text(Content.builder().data(textBody).charset("UTF-8").build())
                        .build())
                .build();
    }

}
