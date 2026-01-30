package com.codejam.auth.dto.request;

import lombok.Data;

@Data
public class ValidateResetTokenRequest {
    private String resetToken;
    private String email;
    private String newPassword;

    public void setEmail(String email) {
        this.email = email != null ? email.trim().toLowerCase() : null;
    }
}
