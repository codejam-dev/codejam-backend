package com.codejam.auth.service;

import com.codejam.auth.dto.request.OauthExchangeRequest;
import com.codejam.auth.dto.response.OAuthCodeResponse;
import com.codejam.commons.exception.CustomException;
import com.codejam.commons.util.ObjectUtils;
import com.codejam.commons.service.RedisService;
import com.codejam.commons.util.proxyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import static com.codejam.auth.util.Constants.*;

/**
 * Production-grade OAuth Code Service with mandatory PKCE validation.
 * SECURITY FEATURES:
 * - Single-use codes (deleted after exchange)
 * - 5-minute expiration
 * - Mandatory PKCE validation (S256 only)
 * - Secure random code generation
 * - Cryptographic challenge verification
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthCodeService {

    private final RedisService redisService;
    private final SecureRandom secureRandom;
    private final proxyUtils proxyUtils;
    /**
     * Generates a secure random code and stores OAuth data in Redis with PKCE challenge.
     *
     * @param token JWT token
     * @param email User email
     * @param name User name
     * @param userId User ID
     * @param avatar Profile image URL (optional)
     * @param codeChallenge PKCE code challenge (SHA256 hash of code_verifier) - REQUIRED
     * @return OAuth code that can be exchanged for token
     * @throws CustomException if codeChallenge is missing (PKCE is mandatory)
     */
    public String generateCode(
            String token,
            String email,
            String name,
            String userId,
            String avatar,
            String codeChallenge) {

        if (ObjectUtils.isNullOrEmpty(codeChallenge)) {
            throw new CustomException(
                    "MISSING_CODE_CHALLENGE",
                    "PKCE code_challenge is required for OAuth code generation",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!isValidCodeChallenge(codeChallenge)) {
            log.error("Invalid PKCE code_challenge format during code generation");
            throw new CustomException(
                    "INVALID_CODE_CHALLENGE",
                    "Invalid code_challenge format. Must be Base64 URL-safe (43-128 chars)",
                    HttpStatus.BAD_REQUEST
            );
        }

        String code = generateSecureCode();
        OAuthCodeResponse oauthData = OAuthCodeResponse.builder()
                .token(token)
                .email(email)
                .name(name)
                .userId(userId)
                .avatar(avatar)
                .codeChallenge(codeChallenge)
                .build();

        String key = proxyUtils.generateRedisKey(OAUTH_CODE_REDIS_PREFIX, code);
        redisService.set(key, oauthData.toJson(), OAUTH_CODE_EXPIRY);
        log.info("OAuth code generated successfully. User: {}, Code expires in {} seconds", email, OAUTH_CODE_EXPIRY);
        return code;
    }

    /**
     * Exchanges an OAuth code for user data and token.
     * MANDATORY PKCE validation using S256 (SHA256).
     *
     * @param request PKCE code verifier (plain text, sent by frontend) - REQUIRED
     * @return OAuthCodeResponse with token and user data
     * @throws CustomException if code is invalid, expired, or PKCE validation fails
     */
    public OAuthCodeResponse exchangeCode(OauthExchangeRequest request) {

        if (!isValidCodeVerifier(request.getCodeVerifier())) {
            throw new CustomException(
                    "INVALID_CODE_VERIFIER_FORMAT",
                    "Invalid code_verifier format. Must be URL-safe (43-128 chars)",
                    HttpStatus.BAD_REQUEST
            );
        }

        log.info("OAuth code exchange request received");

        String key = proxyUtils.generateRedisKey(OAUTH_CODE_REDIS_PREFIX, request.getCode());
        String storedData = redisService.get(key);

        if (ObjectUtils.isNullOrEmpty(storedData)) {
            log.warn("Invalid or expired OAuth code attempted");
            throw new CustomException(
                    "INVALID_OAUTH_CODE",
                    "OAuth code is invalid or has expired",
                    HttpStatus.BAD_REQUEST
            );
        }

        OAuthCodeResponse oauthData = OAuthCodeResponse.fromJson(storedData);
        String storedChallenge = oauthData.getCodeChallenge();

        if (ObjectUtils.isNullOrEmpty(storedChallenge)) {
            redisService.delete(key);
            log.error("PKCE code_challenge missing in stored OAuth data");
            throw new CustomException(
                    "PKCE_REQUIRED",
                    "PKCE validation failed: code_challenge not found",
                    HttpStatus.BAD_REQUEST
            );
        }

        String computedChallenge = computeCodeChallenge(request.getCodeVerifier());

        if (!computedChallenge.equals(storedChallenge)) {
            redisService.delete(key);
            log.warn("PKCE validation failed: code_verifier does not match challenge");
            throw new CustomException(
                    "INVALID_CODE_VERIFIER",
                    "Code verifier does not match challenge",
                    HttpStatus.BAD_REQUEST
            );
        }
        redisService.delete(key);
        log.info("OAuth code exchange successful. User: {}", oauthData.getEmail());
        return oauthData;
    }

    /**
     * Computes PKCE code challenge from code verifier using S256 (SHA256).
     * ALGORITHM (RFC 7636):
     * 1. Hash code_verifier using SHA-256
     * 2. Encode hash as Base64 URL-safe (no padding)
     *
     * @param codeVerifier The plain text code verifier (43-128 characters)
     * @return Base64 URL-safe encoded SHA256 hash (code challenge)
     * @throws RuntimeException if hashing fails
     */
    private String computeCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute code challenge", e);
        }
    }

    /**
     * Validates code_challenge format according to RFC 7636.
     * - Must be Base64 URL-safe encoded
     * - Length: 43-128 characters
     */
    private boolean isValidCodeChallenge(String codeChallenge) {
        if (codeChallenge == null || codeChallenge.length() < 43 || codeChallenge.length() > 128) {
            return false;
        }
        // Base64 URL-safe: A-Z, a-z, 0-9, -, _
        return codeChallenge.matches("^[A-Za-z0-9_-]+$");
    }

    /**
     * Validates code_verifier format according to RFC 7636.
     * - Must be URL-safe
     * - Length: 43-128 characters
     */
    private boolean isValidCodeVerifier(String codeVerifier) {
        if (codeVerifier == null || codeVerifier.length() < 43 || codeVerifier.length() > 128) {
            return false;
        }
        // URL-safe characters: A-Z, a-z, 0-9, -, ., _, ~
        return codeVerifier.matches("^[A-Za-z0-9._~-]+$");
    }

    /**
     * Generates a secure random code (32 characters, alphanumeric).
     */
    private String generateSecureCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder code = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            code.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return code.toString();
    }

}
