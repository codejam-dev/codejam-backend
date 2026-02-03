package com.codejam.auth.service;

import com.codejam.auth.service.email.provider.EmailProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "email.provider", havingValue = "resend", matchIfMissing = true)
@RequiredArgsConstructor
public class ResendEmailProvider implements EmailProvider {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.resend.com")
            .build();

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.from-email}")
    private String fromEmail;

    @Override
    public void sendEmail(String toEmail, String subject, String htmlContent) {
        log.info("Sending email FROM {} TO {}", fromEmail, toEmail);

        Map<String, Object> request = Map.of(
                "from", fromEmail,
                "to", List.of(toEmail),
                "subject", subject,
                "html", htmlContent
        );

        try {
            String response = webClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Resend API error response: {}", errorBody);
                                        return Mono.error(new RuntimeException("Resend API error: " + errorBody));
                                    }))
                    .bodyToMono(String.class)
                    .block();

            log.info("Email sent successfully: {}", response);

        } catch (Exception e) {
            log.error("Failed to send email via Resend", e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }
}