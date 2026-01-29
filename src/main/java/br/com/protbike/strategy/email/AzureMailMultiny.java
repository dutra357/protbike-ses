//package br.com.protbike.strategy.email;
//
//import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
//import br.com.protbike.exceptions.taxonomy.EnvioSucesso;
//import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
//import br.com.protbike.records.BoletoNotificacaoMessage;
//import br.com.protbike.records.enuns.CanalEntrega;
//import br.com.protbike.utils.FormatarEmailTextoV1;
//import br.com.protbike.utils.FormatarEmailV3;
//import com.azure.communication.email.EmailAsyncClient;
//import com.azure.communication.email.EmailClientBuilder;
//import com.azure.communication.email.models.EmailAddress;
//import com.azure.communication.email.models.EmailMessage;
//import com.azure.communication.email.models.EmailSendResult;
//import com.azure.communication.email.models.EmailSendStatus;
//import com.azure.core.http.jdk.httpclient.JdkHttpClientBuilder;
//import io.smallrye.mutiny.Uni;
//import jakarta.enterprise.context.ApplicationScoped;
//import org.eclipse.microprofile.config.inject.ConfigProperty;
//import org.jboss.logging.Logger;
//
//import javax.crypto.Mac;
//import javax.crypto.spec.SecretKeySpec;
//import java.nio.charset.StandardCharsets;
//import java.security.InvalidKeyException;
//import java.security.NoSuchAlgorithmException;
//import java.time.Duration;
//import java.util.Base64;
//
//
//@ApplicationScoped
//public class AzureMailMultiny {
//
//    private static final Logger LOG = Logger.getLogger(AzureMail.class);
//    private final EmailAsyncClient emailAsyncClient;
//
//    @ConfigProperty(name = "unsubscribe.secret")
//    String unsubscribeSecret;
//
//    public AzureMailMultiny(@ConfigProperty(name = "azure.communication.connection.string") String connectionString) {
//        this.emailAsyncClient = new EmailClientBuilder()
//                .connectionString(connectionString)
//                .httpClient(new JdkHttpClientBuilder().build())
//                .buildAsyncClient();
//    }
//
////    @Override
//    public CanalEntrega pegarCanal() {
//        return CanalEntrega.SEM_CANAL;
//    }
//
////    @Override
//    public Uni<ResultadoEnvio> enviarMensagem(BoletoNotificacaoMessage notificacaoMsg) {
//
//        return Uni.createFrom().item(() -> montarEmail(notificacaoMsg))
//                .onItem().transformToUni(message ->
//                        Uni.createFrom().completionStage(
//                                emailAsyncClient.beginSend(message)
//                                        .last()
//                                        .toFuture()
//                        )
//                )
//
//                // timeout real do pipeline
//                .ifNoItem().after(Duration.ofSeconds(8)).fail()
//
//                // sucesso técnico
//                .onItem().transform(result -> interpretarResultado(result.getValue(), notificacaoMsg))
//
//                // retry apenas em falha técnica
//                .onFailure().retry()
//                .withBackOff(Duration.ofMillis(300), Duration.ofSeconds(2))
//                .withJitter(0.3)
//                .atMost(3)
//
//                // fallback final caso estoure tudo
//                .onFailure().recoverWithItem(ex -> {
//                    LOG.errorf(ex, "Azure: Falha definitiva no protocolo %s", notificacaoMsg.numeroProtocolo());
//                    return new EnvioFalhaNaoRetryavel(
//                            notificacaoMsg.numeroProtocolo(),
//                            "Falha técnica definitiva: " + ex.getMessage()
//                    );
//                });
//    }
//
//    private EmailMessage montarEmail(BoletoNotificacaoMessage notificacaoMsg) {
//
//        try {
//            String destinatario = notificacaoMsg.destinatario().email();
//
//            String subject = notificacaoMsg.meta().associacaoApelido().toUpperCase()
//                    + " | Boleto disponível - " + notificacaoMsg.boleto().mesReferente();
//
//            String unsubscribeUrl = "https://protbike.com.br/email-list/unsubscribe?token="
//                    + gerarTokenDescadastro(destinatario);
//
//            String htmlBody = FormatarEmailV3.toHtml(notificacaoMsg, unsubscribeUrl);
//            String txtBody = FormatarEmailTextoV1.toPlainText(notificacaoMsg, unsubscribeUrl);
//
//            EmailMessage message = new EmailMessage()
//                    .setSenderAddress("donotreply@protbike.com.br")
//                    .setReplyTo(new EmailAddress(notificacaoMsg.meta().admEmail()))
//                    .setSubject(subject)
//                    .setBodyHtml(htmlBody)
//                    .setBodyPlainText(txtBody);
//
//            message.setToRecipients(new EmailAddress(destinatario));
//
//            message.getHeaders().put("List-Unsubscribe", "<" + unsubscribeUrl + ">");
//            message.getHeaders().put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
//            message.getHeaders().put("Precedence", "bulk");
//            message.getHeaders().put("X-Mailer", "Protbike Notification Service v2.0");
//
//            return message;
//
//        } catch (Exception e) {
//            throw new RuntimeException("Erro ao preparar mensagem", e);
//        }
//    }
//
//    private ResultadoEnvio interpretarResultado(EmailSendResult result, BoletoNotificacaoMessage msg) {
//
//        if (result.getStatus() == EmailSendStatus.SUCCEEDED) {
//            LOG.infof("Azure: Sucesso. Protocolo=%s, Id=%s",
//                    msg.numeroProtocolo(), result.getId());
//            return new EnvioSucesso(msg.numeroProtocolo());
//        }
//
//        return tratarErroNegocioAzure(result, msg);
//    }
//
//    private ResultadoEnvio tratarErroNegocioAzure(EmailSendResult result, BoletoNotificacaoMessage msg) {
//        String protocolo = msg.numeroProtocolo();
//        String erroMsg = result.getError().getMessage();
//
//        LOG.warnf("Azure: Falha no envio para %s: %s", msg.destinatario().email(), erroMsg);
//
//        return new EnvioFalhaNaoRetryavel(protocolo, "Erro Azure ACS: " + erroMsg);
//    }
//
//    public String gerarTokenDescadastro(String email) {
//        try {
//            long timestamp = System.currentTimeMillis() / 1000;
//            String payload = "v1|" + email + "|" + timestamp;
//
//            Mac mac = Mac.getInstance("HmacSHA256");
//            SecretKeySpec keySpec = new SecretKeySpec(unsubscribeSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
//            mac.init(keySpec);
//
//            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
//            String assinaturaBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
//
//            String token = payload + "|" + assinaturaBase64;
//            return Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
//
//        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
//            throw new RuntimeException("Erro ao gerar token", exception);
//        }
//    }
//}