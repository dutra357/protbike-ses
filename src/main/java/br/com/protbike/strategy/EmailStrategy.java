package br.com.protbike.strategy;

import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
import br.com.protbike.exceptions.taxonomy.EnvioSucesso;
import br.com.protbike.exceptions.taxonomy.ResultadoEnvio;
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
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.sesv2.model.TooManyRequestsException;
import software.amazon.awssdk.services.ses.model.SesException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

@ApplicationScoped
public class EmailStrategy implements NotificacaoStrategy {

    private static final Logger LOG = Logger.getLogger(EmailStrategy.class);
    private final SesV2Client sesClient;

    // Métricas removidas do construtor, pois quem conta agora é o Handler
    public EmailStrategy(SesV2Client sesClient) {
        this.sesClient = sesClient;
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
    @RateLimit(value = 1, window = 2) // SES Sandbox padrão é 1/s, Prod é 14/s+
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

            return new EnvioSucesso(notificacaoMsg.numeroProtocolo());

        } catch (MessageRejectedException e) {
            // Caso 1: Erro de negócio/validação do SES.
            // Retorna o objeto para o processador jogar na DLQ.
            LOG.warnf("Email rejeitado permanentemente (Blacklist/Inválido): %s", email);

            return new EnvioFalhaNaoRetryavel(
                    notificacaoMsg.numeroProtocolo(),
                    "Email rejeitado pelo SES: " + e.getMessage()
            );

        } catch (TooManyRequestsException | ApiCallTimeoutException e) {
            // Caso 2: SES sobrecarregada.
            // Lançamos a exceção para o @Retry capturar, esperar 300ms e tentar de novo.
            LOG.warnf("Throttling no SES para %s. O sistema fará retry.", email);
            throw e;

        } catch (SesException e) {
            // Caso 3: Erro genérico da AWS
            // Lança a exceção para ativar o @Retry e o CircuitBreaker.
            LOG.errorf(e,
                    "Erro SES recuperável. protocolo=%s destinatario=%s awsCode=%s",
                    notificacaoMsg.numeroProtocolo(),
                    email,
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "N/D"
            );
            throw e;

        } catch (Exception e) {
            // Caso 4: Erro desconhecido (NullPointer na formatação, erro de rede local etc).
            // Lançamos para tentar retry se for rede, ou falhar se for código.
            LOG.errorf(e, "Erro inesperado ao enviar email para %s", email);
            throw e;
        }
    }

    private Message messageToHtml(BoletoNotificacaoMessage msg) {
        String subject = msg.meta().associacaoApelido().toUpperCase() + " | Boleto disponível - " + msg.boleto().mesReferente();
        String htmlBody = EmailFormatterHTML.toHtml(msg);
        String textBody = BoletoEmailFormatter.formatarCorpoEmail(msg);

        return Message.builder()
                .subject(Content.builder().data(subject).charset("UTF-8").build())
                .body(Body.builder()
                        .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                        .text(Content.builder().data(textBody).charset("UTF-8").build())
                        .build())
                .build();
    }
}