package com.codejam.auth.service;

import com.codejam.auth.service.email.provider.EmailProvider;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "email.provider", havingValue = "SMTP")
public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;

    @Override
    public void sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(microserviceConfig.getMailUsername(), "CodeJam"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email via SMTP", e);
        }
    }
}
