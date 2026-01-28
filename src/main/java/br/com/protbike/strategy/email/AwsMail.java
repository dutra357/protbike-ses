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
//import software.amazon.awssdk.core.exception.SdkClientException;
//import software.amazon.awssdk.services.sesv2.SesV2AsyncClient;
//import software.amazon.awssdk.services.sesv2.model.*;
//
//import javax.crypto.Mac;
//import javax.crypto.spec.SecretKeySpec;
//import java.nio.charset.StandardCharsets;
//import java.security.InvalidKeyException;
//import java.security.NoSuchAlgorithmException;
//import java.util.Base64;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.CompletionException;
//
//@ApplicationScoped
//public class AwsMail implements NotificacaoStrategy {
//
//    private static final Logger LOG = Logger.getLogger(AwsMail.class);
//    private final SesV2AsyncClient sesClient;
//
//    public AwsMail(SesV2AsyncClient sesClient) {
//        this.sesClient = sesClient;
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
//
//        String email = notificacaoMsg.destinatario().email();
//
//        SendEmailRequest request = SendEmailRequest.builder()
//                .configurationSetName("ses-observabilidade")
//                .fromEmailAddress(notificacaoMsg.meta().associacaoCliente() + " <" + notificacaoMsg.meta().admEmail() + ">")
//                .replyToAddresses(notificacaoMsg.meta().admEmail())
//                .destination(Destination.builder().toAddresses(email).build())
//                .content(EmailContent.builder()
//                        .simple(messageToHtml(notificacaoMsg))
//                        .build())
//                .build();
//
//        return sesClient.sendEmail(request)
//                .handle((response, exception) -> {
//
//                    // Se não houve exceção, sucesso total
//                    if (exception == null) {
//                        LOG.debugf("SES aceitou envio. protocolo=%s id=%s", notificacaoMsg.numeroProtocolo(), response.messageId());
//                        return new EnvioSucesso(notificacaoMsg.numeroProtocolo());
//                    }
//
//                    return aplicarTaxonomiaDeErro(exception, notificacaoMsg);
//                });
//    }
//
//    private Message messageToHtml(BoletoNotificacaoMessage msg) {
//        String unsubscribeUrl = "https://protbike.com.br/email-list/unsubscribe?token="
//                + gerarTokenDescadastro(msg.destinatario().email());
//
//        String subject = msg.meta().associacaoApelido().toUpperCase() + " | Boleto disponível - " + msg.boleto().mesReferente();
//        String htmlBody = FormatarEmailV3.toHtml(msg, unsubscribeUrl);
//
//        return Message.builder()
//                .subject(Content.builder().data(subject).charset("UTF-8").build())
//                .body(Body.builder()
//                        .html(Content.builder().data(htmlBody).charset("UTF-8").build())
//                        .build())
//                .build();
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
//
//    public String gerarTokenDescadastro(String email) {
//        try {
//            long timestamp = System.currentTimeMillis() / 1000;
//            String payload = email + "|" + timestamp;
//            Mac mac = Mac.getInstance("HmacSHA256");
//            SecretKeySpec keySpec = new SecretKeySpec(unsubscribeSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
//            mac.init(keySpec);
//            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
//            String assinaturaBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
//            String token = payload + "|" + assinaturaBase64;
//            return Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
//        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
//            throw new RuntimeException("Erro ao gerar token", exception);
//        }
//    }
//}