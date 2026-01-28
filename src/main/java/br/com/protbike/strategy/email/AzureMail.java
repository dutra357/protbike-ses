package br.com.protbike.strategy.email;

import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
import br.com.protbike.exceptions.taxonomy.EnvioSucesso;
import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
import br.com.protbike.strategy.contract.NotificacaoStrategy;
import br.com.protbike.utils.FormatarEmailTextoV1;
import br.com.protbike.utils.FormatarEmailV3;
import com.azure.communication.email.EmailAsyncClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.*;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class AzureMail implements NotificacaoStrategy {

    private static final Logger LOG = Logger.getLogger(AzureMail.class);
    private final EmailAsyncClient emailAsyncClient;

    @ConfigProperty(name = "unsubscribe.secret")
    String unsubscribeSecret;

    public AzureMail(@ConfigProperty(name = "azure.communication.connection.string") String connectionString) {
        this.emailAsyncClient = new EmailClientBuilder()
                .connectionString(connectionString)
                .buildAsyncClient();
    }

    @Override
    public CanalEntrega pegarCanal() {
        return CanalEntrega.EMAIL;
    }

    @Override
    @Retry(maxRetries = 3, delay = 400)
    @Timeout(8000)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10_000)
    @RateLimit(value = 1, window = 1)
    public CompletableFuture<ResultadoEnvio> enviarMensagem(BoletoNotificacaoMessage notificacaoMsg) {

        String destinatario = notificacaoMsg.destinatario().email();
        String emailRemetente = notificacaoMsg.meta().admEmail();

        String subject = notificacaoMsg.meta().associacaoApelido().toUpperCase()
                + " | Boleto disponível - " + notificacaoMsg.boleto().mesReferente();

        String unsubscribeUrl = "https://protbike.com.br/email-list/unsubscribe?token="
                + gerarTokenDescadastro(destinatario);

        String htmlBody = FormatarEmailV3.toHtml(notificacaoMsg, unsubscribeUrl);
        String txtBody = FormatarEmailTextoV1.toPlainText(notificacaoMsg, unsubscribeUrl);

        try {
            EmailMessage message = new EmailMessage()
                    .setSenderAddress("donotreply@protbike.com.br")
                    .setReplyTo(new EmailAddress(notificacaoMsg.meta().admEmail()))
                    .setSubject(subject)
                    .setBodyHtml(htmlBody)
                    .setBodyPlainText(txtBody);


            message.setToRecipients(new EmailAddress(destinatario));

            message.getHeaders().put("List-Unsubscribe", "<" + unsubscribeUrl + ">");
            message.getHeaders().put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            message.getHeaders().put("Precedence", "bulk");
            message.getHeaders().put("X-Mailer", "Protbike Notification Service v2.0");

            return emailAsyncClient.beginSend(message)

                    .last()
                    .toFuture()
                    .handle((result, exception) -> {

                        if (exception != null) {
                            LOG.errorf(exception, "Azure: Falha técnica no protocolo %s", notificacaoMsg.numeroProtocolo());
                            throw new RuntimeException(exception); // Dispara @Retry
                        }

                        if (result.getValue().getStatus() == EmailSendStatus.SUCCEEDED) {
                            LOG.infof("Azure: Sucesso. Protocolo=%s, Id=%s",
                                    notificacaoMsg.numeroProtocolo(), result.getValue().getId());
                            return new EnvioSucesso(notificacaoMsg.numeroProtocolo());
                        }

                        // Caso o status seja FAILED ou outro erro de negócio da Azure
                        return tratarErroNegocioAzure(result.getValue(), notificacaoMsg);
                    });

        } catch (Exception e) {
            LOG.error("Erro ao preparar mensagem para Azure", e);
            return CompletableFuture.completedFuture(
                    new EnvioFalhaNaoRetryavel(notificacaoMsg.numeroProtocolo(), "Erro Preparação: " + e.getMessage())
            );
        }
    }

    private ResultadoEnvio tratarErroNegocioAzure(EmailSendResult result, BoletoNotificacaoMessage msg) {
        String protocolo = msg.numeroProtocolo();
        String erroMsg = result.getError().getMessage();

        LOG.warnf("Azure: Falha no envio para %s: %s", msg.destinatario().email(), erroMsg);

        // Se o erro for de validação ou algo que não adianta tentar de novo
        return new EnvioFalhaNaoRetryavel(protocolo, "Erro Azure ACS: " + erroMsg);
    }

    public String gerarTokenDescadastro(String email) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            String payload = email + "|" + timestamp;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(unsubscribeSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String assinaturaBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            String token = payload + "|" + assinaturaBase64;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new RuntimeException("Erro ao gerar token", exception);
        }
    }
}