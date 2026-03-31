package com.codejam.commons.constant;

/**
 * JWT scope values issued by auth and enforced at the API boundary.
 */
public final class JwtScopeConstants {

    private JwtScopeConstants() {}

    public static final String API_READ = "api:read";
    public static final String API_WRITE = "api:write";
    public static final String OTP_GENERATE = "otp:generate";
    public static final String OTP_VALIDATE = "otp:validate";
}
