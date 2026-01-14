package br.com.protbike.input;

import br.com.protbike.exceptions.NonRetryableException;
import br.com.protbike.exceptions.RetryableException;
import br.com.protbike.records.EmailJob;
import br.com.protbike.service.PoisonQueuePublisher;
import br.com.protbike.service.SesMailer;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class SqsToSesHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final ObjectMapper mapper;
    private final SesMailer mailer;
    private final PoisonQueuePublisher poison;

    public SqsToSesHandler(ObjectMapper mapper, SesMailer mailer, PoisonQueuePublisher poison) {
        this.mapper = mapper;
        this.mailer = mailer;
        this.poison = poison;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            try {
                EmailJob job = parseAndValidate(msg.getBody());
                mailer.send(job);

            } catch (NonRetryableException e) {
                // NÃO reprocessar: manda pra poison queue e NÃO inclui em failures (ACK)
                safePoison(msg, e, context);

            } catch (RetryableException e) {
                // Reprocessar: inclui em failures
                failures.add(new SQSBatchResponse.BatchItemFailure(msg.getMessageId()));
                context.getLogger().log("Retryable failure messageId=" + msg.getMessageId() + " err=" + e + "\n");

            } catch (Exception e) {
                // fallback conservador: trata como retryable (melhor que “sumir” com msg importante)
                failures.add(new SQSBatchResponse.BatchItemFailure(msg.getMessageId()));
                context.getLogger().log("Unexpected failure messageId=" + msg.getMessageId() + " err=" + e + "\n");
            }
        }

        return new SQSBatchResponse(failures);
    }

    private EmailJob parseAndValidate(String body) {
        try {
            // seu record EmailJob já valida non-blank no construtor
            return mapper.readValue(body, EmailJob.class);
        } catch (JsonProcessingException e) {
            throw new NonRetryableException("Invalid JSON payload", e);
        } catch (IllegalArgumentException e) {
            // vindo do construtor do record (nonBlank)
            throw new NonRetryableException("Validation error: " + e.getMessage(), e);
        }
    }

    private void safePoison(SQSEvent.SQSMessage msg, NonRetryableException e, Context ctx) {
        try {
            poison.publish(msg.getMessageId(), msg.getBody(), e.getMessage());
            ctx.getLogger().log("Poisoned messageId=" + msg.getMessageId() + " reason=" + e.getMessage() + "\n");
        } catch (Exception pubErr) {
            // Se não conseguir publicar na poison queue, melhor reprocessar do que perder
            throw new RetryableException("Failed to publish to poison queue", pubErr);
        }
    }
}