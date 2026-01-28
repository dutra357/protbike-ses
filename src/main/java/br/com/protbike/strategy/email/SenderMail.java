//package br.com.protbike.strategy.email;
//
//import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
//import br.com.protbike.exceptions.taxonomy.EnvioSucesso;
//import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
//import br.com.protbike.records.BoletoNotificacaoMessage;
//import br.com.protbike.records.enuns.CanalEntrega;
//import br.com.protbike.strategy.contract.NotificacaoStrategy;
//import br.com.protbike.utils.FormatarEmailTextoV1;
//import br.com.protbike.utils.FormatarEmailV3;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import io.smallrye.faulttolerance.api.RateLimit;
//import jakarta.enterprise.context.ApplicationScoped;
//import org.eclipse.microprofile.config.inject.ConfigProperty;
//import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
//import org.eclipse.microprofile.faulttolerance.Retry;
//import org.eclipse.microprofile.faulttolerance.Timeout;
//import org.jboss.logging.Logger;
//
//import javax.crypto.Mac;
//import javax.crypto.spec.SecretKeySpec;
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.nio.charset.StandardCharsets;
//import java.security.InvalidKeyException;
//import java.security.NoSuchAlgorithmException;
//import java.util.Base64;
//import java.util.Map;
//import java.util.concurrent.CompletableFuture;
//
//@ApplicationScoped
//public class SenderMail implements NotificacaoStrategy {
//
//    private static final Logger LOG = Logger.getLogger(SenderMail.class);
//    private final HttpClient httpClient;
//    private final ObjectMapper objectMapper;
//
//
//    public SenderMail(ObjectMapper objectMapper) {
//        this.objectMapper = objectMapper;
//        this.httpClient = HttpClient.newBuilder()
//                .version(HttpClient.Version.HTTP_2)
//                .build();
//    }
//
//    @ConfigProperty(name = "sender.api.token")
//    String senderToken;
//
//    @ConfigProperty(name = "unsubscribe.secret")
//    String unsubscribeSecret;
//
//    @Override
//    public CanalEntrega pegarCanal() {
//        return CanalEntrega.SEM_CANAL;
//    }
//
//    @Override
//    @Retry(maxRetries = 3, delay = 400)
//    @Timeout(6000)
//    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10_000)
//    @RateLimit(value = 1, window = 1)
//    public CompletableFuture<ResultadoEnvio> enviarMensagem(BoletoNotificacaoMessage notificacaoMsg) {
//
//        String destinatario = notificacaoMsg.destinatario().email();
//        String nomeRemetente = notificacaoMsg.meta().associacaoCliente();
//        String emailRemetente = notificacaoMsg.meta().admEmail();
//
//        String subject = notificacaoMsg.meta().associacaoApelido().toUpperCase()
//                + " | Boleto disponível - " + notificacaoMsg.boleto().mesReferente();
//
//        String unsubscribeUrl = "https://protbike.com.br/email-list/unsubscribe?token="
//                + gerarTokenDescadastro(notificacaoMsg.destinatario().email());
//
//        String txtBody = FormatarEmailTextoV1.toPlainText(notificacaoMsg, unsubscribeUrl);
//
//        try {
//            // Montagem do Payload JSON (Endpoint v2/message/send)
//            Map<String, Object> body = Map.of(
//                    "from", Map.of(
//                            "email", emailRemetente,
//                            "name", nomeRemetente
//                    ),
//                    "to", Map.of(
//                            "email", destinatario,
//                            "name", notificacaoMsg.destinatario().nome()
//                    ),
//                    "subject", subject,
//                    "html", FormatarEmailV3.toHtml(notificacaoMsg, unsubscribeUrl),
//                    "text", txtBody
//            );
//
//            String jsonPayload = objectMapper.writeValueAsString(body);
//
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create("https://api.sender.net/v2/message/send"))
//                    .header("Authorization", "Bearer " + senderToken)
//                    .header("Content-Type", "application/json")
//                    .header("Accept", "application/json")
//                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
//                    .build();
//
//            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
//                    .handle((response, exception) -> {
//
//                        if (exception != null) {
//                            LOG.errorf(exception, "Sender: Falha técnica no protocolo %s", notificacaoMsg.numeroProtocolo());
//                            throw new RuntimeException(exception); // Dispara @Retry
//                        }
//
//                        int statusCode = response.statusCode();
//
//                        if (statusCode >= 200 && statusCode < 300) {
//                            LOG.infof("Sender: Sucesso. Protocolo=%s", notificacaoMsg.numeroProtocolo());
//                            return new EnvioSucesso(notificacaoMsg.numeroProtocolo());
//                        }
//
//                        return tratarErroHttp(statusCode, response.body(), notificacaoMsg);
//                    });
//
//        } catch (Exception e) {
//            LOG.error("Erro ao serializar JSON para o Sender", e);
//            return CompletableFuture.completedFuture(
//                    new EnvioFalhaNaoRetryavel(notificacaoMsg.numeroProtocolo(), "Erro Serialização: " + e.getMessage())
//            );
//        }
//    }
//
//    private ResultadoEnvio tratarErroHttp(int statusCode, String responseBody, BoletoNotificacaoMessage msg) {
//        String protocolo = msg.numeroProtocolo();
//
//        // Erros de Client (4xx) exceto Throttling
//        if (statusCode >= 400 && statusCode < 500 && statusCode != 429) {
//            LOG.warnf("Sender: Falha fatal (4xx) para %s: %s", msg.destinatario().email(), responseBody);
//            return new EnvioFalhaNaoRetryavel(protocolo, "Erro API Sender (" + statusCode + "): " + responseBody);
//        }
//
//        // Erros de Servidor (5xx) ou Throttling (429) -> Força o Retry
//        LOG.errorf("Sender: Erro transiente (%d). Tentando novamente...", statusCode);
//        throw new RuntimeException("Erro transiente Sender API: " + statusCode);
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