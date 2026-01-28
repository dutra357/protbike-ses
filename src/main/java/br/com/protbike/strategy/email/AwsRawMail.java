//package br.com.protbike.strategy.email;
//
//import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
//import br.com.protbike.exceptions.taxonomy.EnvioSucesso;
//import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
//import br.com.protbike.records.BoletoNotificacaoMessage;
//import br.com.protbike.records.enuns.CanalEntrega;
//import br.com.protbike.strategy.contract.NotificacaoStrategy;
//import br.com.protbike.utils.FormatarEmailV3;
//import io.smallrye.faulttolerance.api.RateLimit;
//import jakarta.enterprise.context.ApplicationScoped;
//import org.eclipse.microprofile.config.inject.ConfigProperty;
//import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
//import org.eclipse.microprofile.faulttolerance.Retry;
//import org.eclipse.microprofile.faulttolerance.Timeout;
//import org.jboss.logging.Logger;
//import software.amazon.awssdk.core.SdkBytes;
//import software.amazon.awssdk.core.exception.SdkClientException;
//import software.amazon.awssdk.services.sesv2.SesV2AsyncClient;
//import software.amazon.awssdk.services.sesv2.model.*;
//
//import javax.crypto.Mac;
//import javax.crypto.spec.SecretKeySpec;
//import java.nio.charset.StandardCharsets;
//import java.security.InvalidKeyException;
//import java.security.NoSuchAlgorithmException;
//import java.time.ZoneOffset;
//import java.time.ZonedDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.Base64;
//import java.util.UUID;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.CompletionException;
//
//@ApplicationScoped
//public class AwsRawMail implements NotificacaoStrategy {
//
//    private static final Logger LOG = Logger.getLogger(AwsRawMail.class);
//    private final SesV2AsyncClient sesAsyncClient;
//
//    public AwsRawMail(SesV2AsyncClient sesAsyncClient) {
//        this.sesAsyncClient = sesAsyncClient;
//    }
//
//    @Override
//    public CanalEntrega pegarCanal() {
//        return CanalEntrega.SEM_CANAL;
//    }
//
//    @ConfigProperty(name = "unsubscribe.secret")
//    String unsubscribeSecret;
//
//    @Override
//    @Retry(maxRetries = 3, delay = 400)
//    @Timeout(4000)
//    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10_000)
//    @RateLimit(value = 1, window = 1)
//    public CompletableFuture<ResultadoEnvio> enviarMensagem(BoletoNotificacaoMessage notificacaoMsg) {
//        String nl = "\r\n";
//        String destinatario = notificacaoMsg.destinatario().email();
//        String nomeRemetente = notificacaoMsg.meta().associacaoCliente();
//        String emailRemetente = notificacaoMsg.meta().admEmail();
//
//        String subject = notificacaoMsg.meta().associacaoApelido().toUpperCase()
//                + " | Boleto disponível - " + notificacaoMsg.boleto().mesReferente();
//
//        String unsubscribeUrl = "https://protbike.com.br/email-list/unsubscribe?token="
//                + gerarTokenDescadastro(destinatario);
//
//        String htmlBody = FormatarEmailV3.toHtml(notificacaoMsg, unsubscribeUrl);
//
//        // Encode do corpo em Base64 para garantir integridade (RFC 2045)
//        String encodedBody = Base64.getMimeEncoder().encodeToString(htmlBody.getBytes(StandardCharsets.UTF_8));
//
//        // RFC 5322 Date format
//        String date = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME);
//
//        // Message-ID robusto para rastreabilidade
//        String messageId = "<" + UUID.randomUUID() + "@" + emailRemetente.split("@")[1] + ">";
//
//        // Construção do Raw Email com Headers de conformidade
//        StringBuilder rawEmail = new StringBuilder();
//        rawEmail.append("From: ").append(nomeRemetente).append(" <").append(emailRemetente).append(">").append(nl);
//        rawEmail.append("To: ").append(destinatario).append(nl);
//        rawEmail.append("Reply-To: ").append(emailRemetente).append(nl);
//        rawEmail.append("Subject: ").append(subject).append(nl);
//        rawEmail.append("Date: ").append(date).append(nl);
//        rawEmail.append("Message-ID: ").append(messageId).append(nl);
//        rawEmail.append("X-Mailer: Protbike Notification Service v2.0").append(nl);
//        rawEmail.append("MIME-Version: 1.0").append(nl);
//        rawEmail.append("Content-Type: text/html; charset=UTF-8").append(nl);
//        rawEmail.append("Content-Transfer-Encoding: base64").append(nl);
//
//        // RFC 8058: One-Click Unsubscribe (O que a AWS ama ver)
//        rawEmail.append("List-Unsubscribe: <").append(unsubscribeUrl).append(">").append(nl);
//        rawEmail.append("List-Unsubscribe-Post: List-Unsubscribe=One-Click").append(nl);
//
//        // Precedência para evitar filtros de spam agressivos em e-mails automáticos
//        rawEmail.append("Precedence: bulk").append(nl);
//
//        // Linha em branco obrigatória entre headers e body
//        rawEmail.append(nl);
//        rawEmail.append(encodedBody);
//
//        SdkBytes data = SdkBytes.fromUtf8String(rawEmail.toString());
//
//        SendEmailRequest request = SendEmailRequest.builder()
//                .configurationSetName("ses-observabilidade")
//                .content(EmailContent.builder()
//                        .raw(RawMessage.builder().data(data).build())
//                        .build())
//                .build();
//
//        return sesAsyncClient.sendEmail(request)
//                .handle((response, exception) -> {
//                    if (exception == null) {
//                        LOG.infof("SES: Sucesso. Protocolo=%s, MessageId=%s",
//                                notificacaoMsg.numeroProtocolo(), response.messageId());
//                        return new EnvioSucesso(notificacaoMsg.numeroProtocolo());
//                    }
//
//                    LOG.errorf(exception, "SES: Falha no envio do protocolo %s", notificacaoMsg.numeroProtocolo());
//                    return aplicarTaxonomiaDeErro(exception, notificacaoMsg);
//                });
//    }
//
//    public String gerarTokenDescadastro(String email) {
//        try {
//            long timestamp = System.currentTimeMillis() / 1000;
//            String payload = email + "|" + timestamp;
//
//            String secret = unsubscribeSecret;
//
//            Mac mac = Mac.getInstance("HmacSHA256");
//            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
//            mac.init(keySpec);
//
//            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
//            String assinaturaBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
//
//            String token = payload + "|" + assinaturaBase64;
//            return Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
//
//        } catch (NoSuchAlgorithmException exception) {
//            throw new RuntimeException("Algorithm not found for HMAC", exception);
//
//        } catch (InvalidKeyException exception) {
//            throw new RuntimeException("Invalid key for HMAC", exception);
//        }
//    }
//
//    private ResultadoEnvio aplicarTaxonomiaDeErro(Throwable ex, BoletoNotificacaoMessage msg) {
//        // Unwraps a exceção do CompletableFuture
//        Throwable e = (ex instanceof CompletionException) ? ex.getCause() : ex;
//        String email = msg.destinatario().email();
//        String protocolo = msg.numeroProtocolo();
//
//        // CASO 1: Erros de Negócio / Configuração (Fatais) -> DLQ
//        if (e instanceof BadRequestException || e instanceof MessageRejectedException ||
//                e instanceof AccountSuspendedException || e instanceof NotFoundException) {
//
//            LOG.warnf("Falha fatal no envio (sem retry) para %s: %s", email, e.getMessage());
//            return new EnvioFalhaNaoRetryavel(protocolo,
//                    "Rejeitado SES V2 (" + e.getClass().getSimpleName() + "): " + e.getMessage());
//        }
//
//        // CASO 2: Erros Transientes Claros (Rede/Throttling) -> DISPARA RETRY
//        if (e instanceof TooManyRequestsException || e instanceof SdkClientException) {
//            LOG.warnf("Erro transiente (Rede/Throttling) para %s. Tentando novamente...", email);
//            throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
//        }
//
//        // CASO 3: Erro Genérico do Serviço AWS (V2)
//        if (e instanceof SesV2Exception sesEx) {
//            if (sesEx.statusCode() >= 500) {
//                LOG.errorf("Erro interno da AWS SES (Status 5xx). Tentando novamente.");
//                throw new RuntimeException(sesEx); // Dispara Retry
//            }
//
//            LOG.errorf(e, "Erro SES V2 não tratado (Status %d). Enviando para DLQ.", sesEx.statusCode());
//            return new EnvioFalhaNaoRetryavel(protocolo,
//                    "Erro SES V2 (" + sesEx.awsErrorDetails().errorCode() + "): " + e.getMessage());
//        }
//
//        // CASO 4: Erro desconhecido (NullPointer, etc)
//        LOG.errorf(e, "Erro inesperado ao enviar email para %s", email);
//        throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
//    }
//}
