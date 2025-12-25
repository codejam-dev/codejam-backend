package com.codejam.auth.dto.request;

import lombok.*;


@Data
public class ResetPasswordRequest {
    private String email;

    public void setEmail(String email) {
        this.email = email != null ? email.trim().toLowerCase() : null;
    }
}
