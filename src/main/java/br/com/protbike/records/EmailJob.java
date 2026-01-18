package br.com.protbike.records;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public record EmailJob(
        String to,
        String from,
        String subject,
        String html,
        String text
) {
    @JsonCreator
    public EmailJob(
            @JsonProperty(value = "to", required = true) String to,
            @JsonProperty(value = "from", required = true) String from,
            @JsonProperty(value = "subject", required = true) String subject,
            @JsonProperty(value = "html", required = true) String html,
            @JsonProperty(value = "text", required = true) String text
    ) {
        this.to = requiredNonBlank(to, "to");
        this.from = requiredNonBlank(from, "from");
        this.subject = requiredNonBlank(subject, "subject");
        this.html = requiredNonBlank(html, "html");
        this.text = requiredNonBlank(text, "text");
    }

    private static String requiredNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}