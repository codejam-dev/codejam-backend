package com.codejam.auth.service.email.template;

public class PasswordResetEmailTemplate implements EmailTemplate {
    private final String resetLink;

    public PasswordResetEmailTemplate(String resetLink) {
        this.resetLink = resetLink;
    }

    @Override
    public String getSubject() {
        return "🔑 Password Reset Request for CodeJam";
    }

    @Override
    public String getHtmlBody(){
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Password Reset</title>
            </head>
            <body>
                <h2>Password Reset Request</h2>
                <p>Click the link below to reset your password:</p>
                <a href="{}">Reset Password</a>
                <p>If you did not request a password reset, please ignore this email.</p>
            </body>
            </html>
            """.replace("{}", resetLink);
    }
}
