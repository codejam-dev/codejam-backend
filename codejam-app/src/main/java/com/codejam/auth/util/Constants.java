package com.codejam.auth.util;

public class Constants {
    public static final long OTP_REDIS_EXPIRY = 10 * 60;
    public static final String REGISTER_SUCCESS_MESSAGE = "User registered successfully. Please verify your email to activate your account.";
    public static final String OTP_SENT_MESSAGE = "OTP has been sent to your email.";
    public static final String OTP_VERIFIED_MESSAGE = "OTP verified successfully. Your account is now activated.";
    public static final String LOGIN_SUCCESS_MESSAGE = "Login successful.";

    // PKCE Constants
    public static final String PKCE_CODE_CHALLENGE_METHOD_S256 = "S256";
    public static final String PKCE_PARAM_CODE_CHALLENGE = "code_challenge";
    public static final String PKCE_PARAM_CODE_CHALLENGE_METHOD = "code_challenge_method";
    public static final String SESSION_ATTRIBUTE_CODE_CHALLENGE = "oauth_code_challenge";
    public static final String OAUTH_CODE_REDIS_PREFIX = "OAUTH_CODE";
    public static final long OAUTH_CODE_EXPIRY = 5 * 60; // 5 minutes
}

