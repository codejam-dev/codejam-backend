package com.codejam.auth.service;

import com.codejam.auth.service.email.provider.EmailProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "email.provider", havingValue = "resend", matchIfMissing = true)
public class ResendEmailProvider implements EmailProvider {

    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.from-email}")
    private String fromEmail;

    @Override
    public void sendEmail(String toEmail, String subject, String htmlContent) {
        log.info("Sending email FROM {} TO {}", fromEmail, toEmail);

        Map<String, Object> body = Map.of(
                "from", fromEmail,
                "to", List.of(toEmail),
                "subject", subject,
                "html", htmlContent
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            String response = restTemplate.postForEntity(
                    RESEND_URL,
                    new HttpEntity<>(body, headers),
                    String.class
            ).getBody();
            log.info("Email sent successfully: {}", response);
        } catch (Exception e) {
            log.error("Failed to send email via Resend", e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }
}
