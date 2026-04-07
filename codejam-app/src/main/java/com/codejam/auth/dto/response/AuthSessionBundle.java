package com.codejam.auth.dto.response;

/**
 * API response body plus optional refresh JWT for Set-Cookie (never serialize refresh in JSON).
 */
public record AuthSessionBundle(AuthResponse authResponse, String refreshToken) {
    public static AuthSessionBundle of(AuthResponse authResponse, String refreshToken) {
        return new AuthSessionBundle(authResponse, refreshToken);
    }
}
