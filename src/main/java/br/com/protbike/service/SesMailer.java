package br.com.protbike.service;

import br.com.protbike.records.EmailJob;
import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

@ApplicationScoped
public class SesMailer {

    private final SesV2Client sesMailer;

    public SesMailer(SesV2Client ses) {
        this.sesMailer = ses;
    }

    public void send(EmailJob job) {
        // Simples (HTML + text). Para casos avançados (anexos etc), use Raw email.
        EmailContent content = EmailContent.builder()
                .simple(Message.builder()
                        .subject(Content.builder().data(job.subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .html(Content.builder().data(job.html).charset("UTF-8").build())
                                .text(Content.builder().data(job.text).charset("UTF-8").build())
                                .build())
                        .build())
                .build();

        SendEmailRequest req = SendEmailRequest.builder()
                .fromEmailAddress(job.from)
                .destination(Destination.builder().toAddresses(job.to).build())
                .content(content)
                .build();

        sesMailer.sendEmail(req);
    }
}
