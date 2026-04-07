package com.codejam.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OauthExchangeRequest {

    @NotBlank(message = "Authorization code is required")
    private String code;

    @NotBlank(message = "Code verifier is required")
    private String codeVerifier;

    @NotBlank(message = "Device ID is required")
    private String deviceId;
}
