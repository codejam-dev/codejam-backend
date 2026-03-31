package com.codejam.auth.service.email.provider;

public interface EmailProvider {
    void sendEmail(String toEmail, String subject, String htmlContent);

}
