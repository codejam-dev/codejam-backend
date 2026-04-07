package com.codejam.auth.controller;

import com.codejam.auth.dto.request.*;
import com.codejam.auth.dto.response.AuthResponse;
import com.codejam.auth.dto.response.AuthSessionBundle;
import com.codejam.auth.dto.response.OAuthCodeResponse;
import com.codejam.auth.service.AuthService;
import com.codejam.auth.service.OAuthCodeService;
import com.codejam.auth.service.RefreshSessionService;
import com.codejam.auth.util.Constants;
import com.codejam.auth.web.RefreshCookieHelper;
import com.codejam.commons.dto.BaseResponse;
import com.codejam.commons.exception.CustomException;
import com.codejam.commons.util.ObjectUtils;
import jakarta.servlet.http.HttpServletResponse;
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
    private final RefreshSessionService refreshSessionService;
    private final RefreshCookieHelper refreshCookieHelper;

    public AuthController(
            AuthService authService,
            OAuthCodeService oAuthCodeService,
            RefreshSessionService refreshSessionService,
            RefreshCookieHelper refreshCookieHelper) {
        this.authService = authService;
        this.oAuthCodeService = oAuthCodeService;
        this.refreshSessionService = refreshSessionService;
        this.refreshCookieHelper = refreshCookieHelper;
    }

    @PostMapping("/register")
    public BaseResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/generateOtp")
    public BaseResponse generateOtp(@RequestHeader("X-User-Email") String email) {
        return authService.generateOtp(email);
    }

    @PostMapping("/validateOtp")
    public BaseResponse validateOtp(
            @Valid @RequestBody ValidateOtpRequest request,
            @RequestHeader("X-User-Email") String email,
            HttpServletResponse response) {
        request.setEmail(email);
        AuthSessionBundle bundle = authService.verifyEmailAndLogin(request);
        if (bundle.refreshToken() != null) {
            refreshCookieHelper.writeRefreshCookie(response, bundle.refreshToken());
        }
        return BaseResponse.success(bundle.authResponse());
    }

    @PostMapping("/login")
    public BaseResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthSessionBundle bundle = authService.loginWithSession(request);
        if (bundle.refreshToken() != null) {
            refreshCookieHelper.writeRefreshCookie(response, bundle.refreshToken());
        }
        return BaseResponse.success(bundle.authResponse());
    }

    @PostMapping("/oauth/exchange")
    public BaseResponse exchangeOAuthCode(@Valid @RequestBody OauthExchangeRequest request, HttpServletResponse response) {
        OAuthCodeResponse oauthData = oAuthCodeService.exchangeCode(request);
        AuthSessionBundle bundle = authService.establishOAuthSession(oauthData, request.getDeviceId());
        refreshCookieHelper.writeRefreshCookie(response, bundle.refreshToken());
        return BaseResponse.success(bundle.authResponse());
    }

    @PostMapping("/refresh")
    public BaseResponse refresh(
            @CookieValue(name = Constants.COOKIE_REFRESH_TOKEN, required = false) String refreshCookie,
            HttpServletResponse response) {
        if (ObjectUtils.isNullOrEmpty(refreshCookie)) {
            throw new CustomException("INVALID_REFRESH", "Refresh cookie missing", HttpStatus.UNAUTHORIZED);
        }
        var tokens = refreshSessionService.rotateRefreshToken(refreshCookie);
        refreshCookieHelper.writeRefreshCookie(response, tokens.refreshToken());
        AuthResponse body = AuthResponse.builder()
                .accessToken(tokens.accessToken())
                .tokenType("Bearer")
                .message("Token refreshed")
                .build();
        return BaseResponse.success(body);
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
    public BaseResponse logout(
            @CookieValue(name = Constants.COOKIE_REFRESH_TOKEN, required = false) String refreshCookie,
            HttpServletResponse response) {
        refreshSessionService.logoutWithRefreshToken(refreshCookie);
        refreshCookieHelper.clearRefreshCookie(response);
        return BaseResponse.success("Logout successful");
    }

    @PostMapping("/logoutAll")
    public BaseResponse logoutAll(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse response) {
        if (ObjectUtils.isNullOrEmpty(authorization)) {
            throw new CustomException("UNAUTHORIZED", "Missing Authorization header", HttpStatus.UNAUTHORIZED);
        }
        authService.logoutAllDevices(authorization);
        refreshCookieHelper.clearRefreshCookie(response);
        return BaseResponse.success("All devices logged out");
    }
}
