package br.com.protbike.service;

import br.com.protbike.exceptions.NonRetryableException;
import br.com.protbike.exceptions.RetryableException;
import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

@ApplicationScoped
public class SesMailer {

    private final SesV2Client sesMailer;

    public SesMailer(SesV2Client ses) {
        this.sesMailer = ses;
    }

    public void send(br.com.protbike.records.EmailJob job) {
        try {
            EmailContent content = EmailContent.builder()
                    .simple(Message.builder()
                            .subject(Content.builder().data(job.subject()).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(job.html()).charset("UTF-8").build())
                                    .text(Content.builder().data(job.text()).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build();

            SendEmailRequest req = SendEmailRequest.builder()
                    .fromEmailAddress(job.from())
                    .destination(Destination.builder().toAddresses(job.to()).build())
                    .content(content)
                    .build();

            sesMailer.sendEmail(req);

        } catch (SesV2Exception e) {
            int sc = e.statusCode();

            // regra simples e eficiente:
            // 5xx e throttling => retry; 4xx "lógicos" => não retry
            if (sc >= 500 || sc == 429 || isThrottling(e)) {
                throw new RetryableException("SES transient error: " + e.awsErrorDetails().errorCode(), e);
            }
            throw new NonRetryableException("SES non-retryable error: " + e.awsErrorDetails().errorCode(), e);

        } catch (SdkClientException e) {
            // problemas de rede/cliente geralmente são transitórios
            throw new RetryableException("SES client/network error", e);
        }
    }

    private static boolean isThrottling(SesV2Exception e) {
        String code = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "";
        return "ThrottlingException".equals(code) || "TooManyRequestsException".equals(code);
    }
}
