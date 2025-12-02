package com.codejam.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ValidateOtpRequest {

    private String email;

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotBlank(message = "OTP is required")
    private String otp;

    public void setEmail(String email) {
        this.email = email != null ? email.trim().toLowerCase() : null;
    }
}

