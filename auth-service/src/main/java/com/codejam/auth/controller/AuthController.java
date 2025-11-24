package com.codejam.auth.controller;

import com.codejam.commons.dto.BaseResponse;
import com.codejam.dto.request.LoginRequest;
import com.codejam.dto.request.OauthExchangeRequest;
import com.codejam.dto.request.RegisterRequest;
import com.codejam.dto.response.AuthResponse;
import com.codejam.dto.response.OAuthCodeResponse;
import com.codejam.service.AuthService;
import com.codejam.service.OAuthCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    private final OAuthCodeService oAuthCodeService;

    @PostMapping("/register")
    public BaseResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for email: {}", request.getEmail());
        return authService.register(request);
    }
    
    @PostMapping("/login")
    public BaseResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());
        return authService.login(request);
    }

    @PostMapping("/oauth/exchange")
    public BaseResponse<OAuthCodeResponse> exchangeOAuthCode(@Valid @RequestBody OauthExchangeRequest request) {
        log.info("OAuth code exchange request");
        OAuthCodeResponse oauthData = oAuthCodeService.exchangeCode(request);
        return BaseResponse.success(oauthData);
    }

    @GetMapping("/health")
    public BaseResponse<String> health() {
        return BaseResponse.success("Auth service is healthy");
    }
}
