package com.codejam.auth.dto.response;

/**
 * Access + refresh strings for controller to set cookies and JSON body (access only in API response).
 */
public record SessionTokens(String accessToken, String refreshToken) {}
