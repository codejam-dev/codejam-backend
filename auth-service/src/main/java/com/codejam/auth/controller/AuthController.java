package com.codejam.auth.controller;

import com.codejam.auth.dto.request.LoginRequest;
import com.codejam.auth.dto.request.OauthExchangeRequest;
import com.codejam.auth.dto.request.RegisterRequest;
import com.codejam.auth.dto.request.ValidateOtpRequest;
import com.codejam.auth.dto.response.OAuthCodeResponse;
import com.codejam.auth.service.AuthService;
import com.codejam.auth.service.OAuthCodeService;
import com.codejam.commons.dto.BaseResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    private final AuthService authService;
    private final OAuthCodeService oAuthCodeService;

    public AuthController(AuthService authService, OAuthCodeService oAuthCodeService) {
        this.authService = authService;
        this.oAuthCodeService = oAuthCodeService;
    }

    @PostMapping("/register")
    public BaseResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/generateOtp")
    public BaseResponse generateOtp(@RequestHeader("X-User-Email") String email) {
        if (email == null || email.isEmpty()) {
            throw new com.codejam.commons.exception.CustomException("INVALID_TOKEN", "User email not found in request", org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return authService.generateOtp(email);
    }

    @PostMapping("/validateOtp")
    public BaseResponse validateOtp(@Valid @RequestBody ValidateOtpRequest request, @RequestHeader("X-User-Email") String email) {
        if (email == null || email.isEmpty()) {
            throw new com.codejam.commons.exception.CustomException("INVALID_TOKEN", "User email not found in request", org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        request.setEmail(email);
        return authService.verifyEmailAndLogin(request);
    }

    @PostMapping("/login")
    public BaseResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }


    @PostMapping("/oauth/exchange")
    public BaseResponse exchangeOAuthCode(@Valid @RequestBody OauthExchangeRequest request) {
        OAuthCodeResponse oauthData = oAuthCodeService.exchangeCode(request);
        return BaseResponse.success(oauthData);
    }

    @GetMapping("/health")
    public BaseResponse health() {
        return BaseResponse.success("Auth service is healthy");
    }
}
