package com.codejam.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Email is required")
    private String email;
    
    @NotBlank(message = "Password is required")
    private String password;

    public void setEmail(String email) {
        this.email = email != null ? email.trim().toLowerCase() : null;
    }
}
