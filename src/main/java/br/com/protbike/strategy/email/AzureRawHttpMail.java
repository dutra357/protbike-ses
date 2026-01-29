package br.com.protbike.strategy.email;

import br.com.protbike.records.BoletoNotificacaoMessage;
import br.com.protbike.records.enuns.CanalEntrega;
import br.com.protbike.strategy.contract.NotificacaoStrategy;
import br.com.protbike.exceptions.taxonomy.contract.ResultadoEnvio;
import br.com.protbike.exceptions.taxonomy.EnvioSucesso;
import br.com.protbike.exceptions.taxonomy.EnvioFalhaNaoRetryavel;
import br.com.protbike.strategy.email.azure.AzureEmailRequest;
import br.com.protbike.utils.FormatarEmailTextoV1;
import br.com.protbike.utils.FormatarEmailV3;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class AzureRawHttpMail implements NotificacaoStrategy {

    private static final Logger LOG = Logger.getLogger(AzureRawHttpMail.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Dados extraídos da Connection String
    private final String endpoint;
    private final String host;
    private final byte[] accessKey;

    // FIX: Locale.US é obrigatório. Se sair "jan" minúsculo ou "qui" em pt-BR, a Azure nega (401).
    private static final DateTimeFormatter RFC1123_FORMATTER = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneId.of("GMT"));

    @ConfigProperty(name = "unsubscribe.secret")
    String unsubscribeSecret;

    public AzureRawHttpMail(@ConfigProperty(name = "azure.communication.connection.string") String connString,
                            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();

        String[] parts = connString.split(";");
        String tempEndpoint = "";
        String tempKey = "";
        for (String part : parts) {
            if (part.startsWith("endpoint=")) tempEndpoint = part.substring(9);
            if (part.startsWith("accesskey=")) tempKey = part.substring(10);
        }

        // Remove a barra final do endpoint se tiver para evitar duplicidade na URI
        if (tempEndpoint.endsWith("/")) tempEndpoint = tempEndpoint.substring(0, tempEndpoint.length() - 1);

        this.endpoint = tempEndpoint;
        this.host = URI.create(tempEndpoint).getHost(); // Extrai apenas "protbike.communication..."
        this.accessKey = Base64.getDecoder().decode(tempKey);
    }

    @Override
    public CanalEntrega pegarCanal() {
        return CanalEntrega.EMAIL;
    }

    @Override
    public CompletableFuture<ResultadoEnvio> enviarMensagem(BoletoNotificacaoMessage msg) {

        try {
            // 1. Preparar Payload (JSON)
            String destinatario = msg.destinatario().email();
            String unsubscribeUrl = "https://protbike.com.br/email-list/unsubscribe?token="
                    + gerarTokenDescadastro(destinatario);
            String subject = msg.meta().associacaoApelido().toUpperCase()
                    + " | Boleto disponível - " + msg.boleto().mesReferente();

            Map<String, String> customHeaders = new HashMap<>();
            customHeaders.put("List-Unsubscribe", "<" + unsubscribeUrl + ">");
            customHeaders.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            customHeaders.put("X-Mailer", "Protbike Notification Service v2.0");
            customHeaders.put("X-Entity-Ref", msg.meta().associacaoApelido());

            AzureEmailRequest requestBody = new AzureEmailRequest(
                    "donotreply@protbike.com.br",
                    new AzureEmailRequest.EmailContent(
                            subject,
                            FormatarEmailTextoV1.toPlainText(msg, unsubscribeUrl),
                            FormatarEmailV3.toHtml(msg, unsubscribeUrl)
                    ),
                    new AzureEmailRequest.EmailRecipients(List.of(new AzureEmailRequest.EmailAddress(destinatario, null))),
                    (msg.meta().admEmail() != null)
                            ? List.of(new AzureEmailRequest.EmailAddress(msg.meta().admEmail(), msg.meta().associacaoCliente()))
                            : null,
                    null,
                    customHeaders
            );

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            byte[] payloadBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);

            // 2. Preparar Assinatura de Segurança (HMAC)
            String pathAndQuery = "/emails:send?api-version=2023-03-31";
            URI uri = URI.create(endpoint + pathAndQuery);

            String dateHeader = RFC1123_FORMATTER.format(ZonedDateTime.now());

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            String contentHash = Base64.getEncoder().encodeToString(sha256.digest(payloadBytes));

            // FIX: Mantemos o host AQUI na assinatura (obrigatório)
            String stringToSign = String.format(
                    "POST\n%s\n%s;%s;%s",
                    pathAndQuery,
                    dateHeader, host, contentHash
            );

            String signature = computeHmacSha256(stringToSign, accessKey);
            String authHeader = String.format("HMAC-SHA256 SignedHeaders=x-ms-date;host;x-ms-content-sha256&Signature=%s", signature);

            // 3. Montar Request HTTP/2
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("x-ms-date", dateHeader)

                    // .header("host", host) <--- REMOVIDO: O Java adiciona sozinho, e se você por, ele trava.
                    .header("x-ms-content-sha256", contentHash)
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payloadBytes))
                    .build();

            // 4. Envio Assincronamente
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .handle((response, ex) -> {
                        if (ex != null) {
                            LOG.error("Erro de rede no envio Email-Raw", ex);
                            throw new RuntimeException(ex);
                        }

                        if (response.statusCode() == 202) {
                            String msgId = response.headers().firstValue("x-ms-request-id").orElse("N/A");
                            LOG.infof("Email aceito Azure (Raw). ID: %s", msgId);
                            return new EnvioSucesso(msg.numeroProtocolo());
                        } else {
                            LOG.warnf("Falha Azure (Raw). Status: %d. Body: %s. SignedStr: %s",
                                    response.statusCode(), response.body(), stringToSign.replace("\n", "|"));
                            return new EnvioFalhaNaoRetryavel(msg.numeroProtocolo(), "Azure Error: " + response.statusCode());
                        }
                    });

        } catch (Exception e) {
            LOG.error("Erro preparação Raw", e);
            return CompletableFuture.completedFuture(new EnvioFalhaNaoRetryavel(msg.numeroProtocolo(), e.getMessage()));
        }
    }

    private String computeHmacSha256(String data, byte[] key) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key, "HmacSHA256");
            sha256_HMAC.init(secret_key);
            return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao assinar HMAC", e);
        }
    }

    public String gerarTokenDescadastro(String email) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            String payload = email + "|" + timestamp;

            String secret = unsubscribeSecret;

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);

            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String assinaturaBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

            String token = payload + "|" + assinaturaBase64;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));

        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("Algorithm not found for HMAC", exception);

        } catch (InvalidKeyException exception) {
            throw new RuntimeException("Invalid key for HMAC", exception);
        }
    }
}