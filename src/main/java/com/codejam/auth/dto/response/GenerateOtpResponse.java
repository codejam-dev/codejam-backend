package com.codejam.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GenerateOtpResponse {
    private String email;
    private String message;
    private String transactionId;
}

