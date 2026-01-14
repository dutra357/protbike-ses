package br.com.protbike.service;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class PoisonQueuePublisher {

    private final SqsClient sqs;

    public PoisonQueuePublisher(SqsClient sqs) {
        this.sqs = sqs;
    }

    @Inject
    @ConfigProperty(name = "app.poison-queue-url")
    String poisonQueueUrl;

    public void publish(String originalMessageId, String originalBody, String reason) {
        // Aqui dá pra colocar também atributos e/ou JSON estruturado
        String payload = """
            {
              "originalMessageId": "%s",
              "reason": "%s",
              "body": %s
            }
            """.formatted(
                escapeJson(originalMessageId),
                escapeJson(reason),
                toJsonStringLiteral(originalBody)
        );

        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(poisonQueueUrl)
                .messageBody(payload)
                .build());
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // coloca o body original como string JSON (com aspas e escapes), sem tentar “parsear”
    private static String toJsonStringLiteral(String s) {
        return "\"" + escapeJson(s) + "\"";
    }
}
