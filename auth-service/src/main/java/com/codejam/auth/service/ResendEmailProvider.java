package com.codejam.auth.service;

import com.codejam.auth.service.email.provider.EmailProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;


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

        Map<String, Object> request = Map.of(
                "from", fromEmail,
                "to", List.of(toEmail),
                "subject", subject,
                "html", htmlContent
        );

        webClient.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
