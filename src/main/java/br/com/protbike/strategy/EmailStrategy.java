package br.com.protbike.strategy;

import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
import br.com.protbike.exceptions.taxonomy.EnvioFalhaRetryavel;
import br.com.protbike.exceptions.taxonomy.EnvioSucesso;
import br.com.protbike.exceptions.taxonomy.ResultadoEnvio;
import br.com.protbike.metrics.Metricas;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
import br.com.protbike.utils.BoletoEmailFormatter;
import br.com.protbike.utils.EmailFormatterHTML;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
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
    @Retry(maxRetries = 3, delay = 300)
    @Timeout(3000)
    @CircuitBreaker(
            requestVolumeThreshold = 10,
            failureRatio = 0.5,
            delay = 10_000
    )
    @RateLimit(value = 1, window = 2)
    public ResultadoEnvio enviarMensagem(BoletoNotificacaoMessage notificacaoMsg) {

        String email = notificacaoMsg.destinatario().email();

        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .configurationSetName("ses-observabilidade")
                    .fromEmailAddress(
                            notificacaoMsg.meta().associacaoApelido() +
                                    " <" + notificacaoMsg.meta().admEmail() + ">"
                    )
                    .replyToAddresses(notificacaoMsg.meta().admEmail())
                    .destination(Destination.builder()
                            .toAddresses(email)
                            .build())
                    .content(EmailContent.builder()
                            .simple(messageToHtml(notificacaoMsg))
                            .build())
                    .build();

            SendEmailResponse response = sesClient.sendEmail(request);

            LOG.debugf(
                    "SES aceitou envio. protocolo=%s destinatario=%s sesMessageId=%s",
                    notificacaoMsg.numeroProtocolo(),
                    email,
                    response.messageId()
            );

            metricas.enviosSucesso++;
            return new EnvioSucesso(notificacaoMsg.numeroProtocolo());

        } catch (ThrottlingException | TimeoutException e) {
            // Retry faz sentido
            return new EnvioFalhaRetryavel(
                    notificacaoMsg.numeroProtocolo(),
                    "Falha transitória SES: " + e.getMessage()
            );

        } catch (MessageRejectedException e) {
            // Email inválido, nunca vai funcionar
            metricas.enviosFalha++;
            return new EnvioFalhaNaoRetryavel(
                    notificacaoMsg.numeroProtocolo(),
                    "Email rejeitado: " + e.getMessage()
            );

        } catch (SesException e) {
            // Segurança: assume retryável
            metricas.enviosFalha++;
            return new EnvioFalhaRetryavel(
                    notificacaoMsg.numeroProtocolo(),
                    "Erro inesperado: " + e.getClass().getSimpleName()
            );

            LOG.errorf(e,
                    "Erro SES. protocolo=%s destinatario=%s awsCode=%s",
                    notificacaoMsg.numeroProtocolo(),
                    email,
                    e.awsErrorDetails() != null
                            ? e.awsErrorDetails().errorCode()
                            : "desconhecido"
            );

            throw e; // importante! permite retry/circuit breaker funcionar
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
