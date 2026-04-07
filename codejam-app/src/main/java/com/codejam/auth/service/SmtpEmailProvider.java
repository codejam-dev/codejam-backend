package com.codejam.auth.service;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.auth.service.email.provider.EmailProvider;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Conditional(SmtpEmailProvider.OnSmtp.class)
public class SmtpEmailProvider implements EmailProvider {

    static final class OnSmtp implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String v = context.getEnvironment().getProperty("email.provider", "");
            return "smtp".equalsIgnoreCase(v);
        }
    }

    private final JavaMailSender mailSender;
    private final MicroserviceConfig microserviceConfig;

    @Override
    public void sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(microserviceConfig.getMailUsername(), "CodeJam"));
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email via SMTP", e);
        }
    }
}
