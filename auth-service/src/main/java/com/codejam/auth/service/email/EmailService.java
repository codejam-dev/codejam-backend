package com.codejam.auth.service.email;

import com.codejam.auth.service.email.provider.EmailProvider;
import com.codejam.auth.service.email.template.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailProvider emailProvider;

    public void sendOtpEmail(String to, String otp) {
        EmailTemplate template = new OtpEmailTemplate(otp);
        emailProvider.sendEmail(to, template.getSubject(), template.getHtmlBody());
    }

    public void sendPasswordResetEmail(String to, String resetLink) {
        EmailTemplate template = new PasswordResetEmailTemplate(resetLink);
        emailProvider.sendEmail(to, template.getSubject(), template.getHtmlBody());
    }
}