package br.com.protbike.input;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;

@Singleton
public class SqsToSesHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final ObjectMapper mapper;
    private final SesMailer mailer;

    public SqsToSesHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent sqsEvent, Context context) {
        return null;
    }
}
