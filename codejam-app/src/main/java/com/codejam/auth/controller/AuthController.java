package com.codejam.auth.controller;

import com.codejam.auth.dto.request.*;
import com.codejam.auth.dto.response.OAuthCodeResponse;
import com.codejam.auth.service.AuthService;
import com.codejam.auth.service.OAuthCodeService;
import com.codejam.commons.dto.BaseResponse;
import com.codejam.commons.exception.CustomException;
import com.codejam.commons.util.ObjectUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/api/auth")
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
        if (ObjectUtils.isNullOrEmpty(email)) {
            throw new CustomException("INVALID_TOKEN", "User email not found in request", HttpStatus.UNAUTHORIZED);
        }
        return authService.generateOtp(email);
    }

    @PostMapping("/validateOtp")
    public BaseResponse validateOtp(@Valid @RequestBody ValidateOtpRequest request, @RequestHeader("X-User-Email") String email) {
        if (ObjectUtils.isNullOrEmpty(email)) {
            throw new CustomException("INVALID_TOKEN", "User email not found in request", HttpStatus.UNAUTHORIZED);
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

    @PostMapping("/resetPassword")
    public BaseResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/validateResetToken")
    public BaseResponse validateResetToken(@Valid @RequestBody ValidateResetTokenRequest request) {
        return authService.validateResetToken(request);
    }

    @PostMapping("/logout")
    public BaseResponse logout(@RequestHeader("Authorization") String authorizationHeader) {
        return authService.logout(authorizationHeader.substring(7));
    }
}
