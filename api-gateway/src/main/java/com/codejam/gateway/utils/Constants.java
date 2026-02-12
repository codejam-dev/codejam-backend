package com.codejam.gateway.utils;

import java.util.List;

public class Constants {
    public static final String SCOPE_API_READ = "api:read";
    public static final String SCOPE_API_WRITE = "api:write";
    public static final String SCOPE_OTP_GENERATE = "otp:generate";
    public static final String SCOPE_OTP_VALIDATE = "otp:validate";

    public static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/v1/api/auth/register",
            "/v1/api/auth/login",
            "/v1/api/auth/oauth2/authorization/google",
            "/v1/api/auth/oauth2/callback/google",
            "/v1/api/auth/oauth/exchange",
            "/v1/api/auth/resetPassword",
            "/v1/api/auth/validateResetToken"
    );

    // Scopes required for OTP endpoints
    public static final List<String> OTP_ENDPOINTS = List.of(
            "/v1/api/auth/generateOtp",
            "/v1/api/auth/validateOtp"
    );
}

