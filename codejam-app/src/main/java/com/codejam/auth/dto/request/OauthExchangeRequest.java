package com.codejam.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class OauthExchangeRequest {

    @NotBlank(message = "Authorization code is required")
    public String code;

    @NotBlank(message = "Code verifier is required")
    public String codeVerifier;
}
