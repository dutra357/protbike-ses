package br.com.protbike.utils;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;

import java.util.List;

public class SQSEventFactory {

    public static SQSEvent createEvent(String jsonBody) {
        SQSMessage msg = new SQSMessage();
        msg.setBody(jsonBody);
        msg.setMessageId("uuid-teste-123");
        msg.setEventSource("aws:sqs");

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(msg));

        return event;
    }

    public static SQSEvent createBatchEvent(List<String> jsonBodies) {
        List<SQSMessage> messages = jsonBodies.stream().map(body -> {
            SQSMessage m = new SQSMessage();
            m.setBody(body);
            return m;
        }).toList();

        SQSEvent event = new SQSEvent();
        event.setRecords(messages);
        return event;
    }
}