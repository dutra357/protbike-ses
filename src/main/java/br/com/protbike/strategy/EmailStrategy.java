package br.com.protbike.strategy;

import br.com.protbike.metrics.Metricas;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
import br.com.protbike.utils.BoletoEmailFormatter;
import br.com.protbike.utils.EmailFormatterHTML;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import software.amazon.awssdk.services.ses.model.SesException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;


@ApplicationScoped
public class EmailStrategy implements NotificacaoStrategy {

    private static final Logger LOG = Logger.getLogger(EmailStrategy.class.getName());
    private final SesV2Client sesClient;
    private final Metricas metricas;

    public EmailStrategy(SesV2Client sesClient, Metricas metricas) {
        this.sesClient = sesClient;
        this.metricas = metricas;
    }

    @Override
    public CanalEntrega pegarCanal() {
        return CanalEntrega.EMAIL;
    }

    @Override
    public void enviarMensagem(BoletoNotificacaoMessage notificacaoMsg) {

        String email = notificacaoMsg.destinatario().email();

        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .configurationSetName("ses-observabilidade")
                    .fromEmailAddress(notificacaoMsg.meta().associacaoApelido() + " <" + notificacaoMsg.meta().admEmail() + ">")
                    .replyToAddresses(notificacaoMsg.meta().admEmail())
                    .destination(Destination.builder()
                            .toAddresses(email)
                            .build())
                    .content(EmailContent.builder()
                            .simple(messageToHtml(notificacaoMsg))
                            .build())
                    .build();

            SendEmailResponse response = sesClient.sendEmail(request);

            LOG.infof("SES aceitou envio. protocoloId=%s destinatario=%s SesMessageId=%s processamentoId=s%",
                    notificacaoMsg.numeroProtocolo(),
                    email,
                    response.messageId(),
                    notificacaoMsg.processamentoId()
            );

            metricas.enviosSucesso++;

        } catch (SesException e) {
            metricas.enviosFalha++;

            LOG.errorf(e,
                    "Falha ao enviar via SES. protocoloId=%s destinatario=%s codigoErroAws=%s processamentoId=s%",
                    notificacaoMsg.numeroProtocolo(),
                    email,
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "desconhecido",
                    notificacaoMsg.processamentoId()
            );

            // dependendo da sua estratégia:
            // throw e;  -> para reprocessar
            // ou apenas registrar e seguir
        }
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
