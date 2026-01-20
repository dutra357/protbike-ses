package br.com.protbike.strategy;

import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
import br.com.protbike.exceptions.taxonomy.EnvioSucesso;
import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
import br.com.protbike.strategy.contract.NotificacaoStrategy;
import br.com.protbike.utils.FormatarEmailV1;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2AsyncClient;
import software.amazon.awssdk.services.sesv2.model.*;

import java.util.concurrent.CompletionException;

@ApplicationScoped
public class EmailStrategy implements NotificacaoStrategy {

    private static final Logger LOG = Logger.getLogger(EmailStrategy.class);
    private final SesV2AsyncClient sesClient;

    public EmailStrategy(SesV2AsyncClient sesClient) {
        this.sesClient = sesClient;
    }

    @Override
    public CanalEntrega pegarCanal() {
        return CanalEntrega.EMAIL;
    }

    @Override
    @Retry(maxRetries = 3, delay = 300)
    @Timeout(3000)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10_000)
    @RateLimit(value = 14, window = 1)
    @RunOnVirtualThread
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
                    .destination(Destination.builder().toAddresses(email).build())
                    .content(EmailContent.builder()
                            .simple(messageToHtml(notificacaoMsg))
                            .build())
                    .build();

            SendEmailResponse response = enviarEmailComUnwrap(request);

            LOG.debugf("SES aceitou envio. protocolo=%s id=%s", notificacaoMsg.numeroProtocolo(), response.messageId());
            return new EnvioSucesso(notificacaoMsg.numeroProtocolo());

        } catch (BadRequestException | MessageRejectedException | AccountSuspendedException | NotFoundException e) {
            // CASO 1: Erros de Negócio / Configuração (Fatais)
            // BadRequestException: Muito comum na V2 para email inválido ou parameters errados.
            // NotFoundException: Se o ConfigurationSet não existir.
            // Ação: NÃO RETRY -> DLQ
            LOG.warnf("Falha fatal no envio (sem retry) para %s: %s", email, e.getMessage());

            return new EnvioFalhaNaoRetryavel(
                    notificacaoMsg.numeroProtocolo(),
                    "Rejeitado SES V2 (" + e.getClass().getSimpleName() + "): " + e.getMessage()
            );

        } catch (TooManyRequestsException | SdkClientException e) {
            // CASO 2: Erros Transientes Claros
            // TooManyRequests: Throttling da V2.
            // SdkClientException: Rede, DNS, Connection Refused (Java nem chegou na AWS).
            // Ação: RETRY
            LOG.warnf("Erro transiente (Rede/Throttling) para %s. Tentando novamente...", email);
            throw e;

        } catch (SesV2Exception e) {
            // CASO 3: Erro Genérico do Serviço AWS (V2)

            // Se for erro 5xx (Internal Server Error da Amazon), vale o retry.
            if (e.statusCode() >= 500) {
                LOG.errorf("Erro interno da AWS SES (Status 5xx). Tentando novamente.");
                throw e;
            }

            // Se for 4xx que não capturamos acima (ex: MailFromDomainNotVerifiedException), é fatal.
            LOG.errorf(e, "Erro SES V2 não tratado (Status %d). Enviando para DLQ.", e.statusCode());
            return new EnvioFalhaNaoRetryavel(
                    notificacaoMsg.numeroProtocolo(),
                    "Erro SES V2 (" + e.awsErrorDetails().errorCode() + "): " + e.getMessage()
            );

        } catch (Exception e) {
            // CASO 4: Erro desconhecido (NullPointer, etc)
            LOG.errorf(e, "Erro inesperado ao enviar email para %s", email);
            throw e; // CircuitBreaker vai contar isso como falha
        }
    }

    private Message messageToHtml(BoletoNotificacaoMessage msg) {
        String subject = msg.meta().associacaoApelido().toUpperCase() + " | Boleto disponível - " + msg.boleto().mesReferente();
        String htmlBody = FormatarEmailV1.toHtml(msg);

        // Na V2, a estrutura Message -> Body -> Content se mantém similar
        return Message.builder()
                .subject(Content.builder().data(subject).charset("UTF-8").build())
                .body(Body.builder()
                        .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                        .build())
                .build();
    }

    private SendEmailResponse enviarEmailComUnwrap(SendEmailRequest request) {
        try {
            return sesClient.sendEmail(request).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }
}