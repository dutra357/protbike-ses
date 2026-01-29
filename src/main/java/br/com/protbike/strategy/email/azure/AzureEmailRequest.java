package br.com.protbike.strategy.email.azure;


import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.Map;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AzureEmailRequest(
        String senderAddress,
        EmailContent content,
        EmailRecipients recipients,
        List<EmailAddress> replyTo,
        List<EmailAttachment> attachments,
        Map<String, String> headers
) {

    // FIX: Adicionado JsonInclude aqui para remover o displayName: null
    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmailAddress(String address, String displayName) {}

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmailContent(String subject, String plainText, String html) {}

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmailRecipients(List<EmailAddress> to) {}

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmailAttachment(String name, String contentType, String contentInBase64) {}
}