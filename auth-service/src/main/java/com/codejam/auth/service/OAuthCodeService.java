package com.codejam.auth.service;

import com.codejam.dto.request.OauthExchangeRequest;
import com.codejam.dto.response.OAuthCodeResponse;
import com.codejam.commons.exception.CustomException;
import com.codejam.commons.util.Constants;
import com.codejam.commons.util.ObjectUtil;
import com.codejam.commons.util.RedisKeyUtil;
import com.codejam.commons.util.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthCodeService {

    private final RedisService redisService;
    private final RedisKeyUtil redisKeyUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateCode(
            String token,
            String email,
            String name,
            String userId,
            String avatar,
            String codeChallenge) {

        if (ObjectUtil.isNullOrEmpty(codeChallenge)) {
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

        String key = redisKeyUtil.generateRedisKey(Constants.OAUTH_CODE_REDIS_PREFIX, code);
        redisService.set(key, oauthData.toJson(), Constants.OAUTH_CODE_EXPIRY);
        log.info("OAuth code generated successfully. User: {}, Code expires in {} seconds", email, Constants.OAUTH_CODE_EXPIRY);
        return code;
    }

    public OAuthCodeResponse exchangeCode(OauthExchangeRequest request) {

        if (!isValidCodeVerifier(request.getCodeVerifier())) {
            throw new CustomException(
                    "INVALID_CODE_VERIFIER_FORMAT",
                    "Invalid code_verifier format. Must be URL-safe (43-128 chars)",
                    HttpStatus.BAD_REQUEST
            );
        }

        log.info("OAuth code exchange request received");

        String key = redisKeyUtil.generateRedisKey(Constants.OAUTH_CODE_REDIS_PREFIX, request.getCode());
        String storedData = redisService.get(key);

        if (ObjectUtil.isNullOrEmpty(storedData)) {
            log.warn("Invalid or expired OAuth code attempted");
            throw new CustomException(
                    "INVALID_OAUTH_CODE",
                    "OAuth code is invalid or has expired",
                    HttpStatus.BAD_REQUEST
            );
        }

        OAuthCodeResponse oauthData = OAuthCodeResponse.fromJson(storedData);
        String storedChallenge = oauthData.getCodeChallenge();

        if (ObjectUtil.isNullOrEmpty(storedChallenge)) {
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

    private String computeCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute code challenge", e);
        }
    }

    private boolean isValidCodeChallenge(String codeChallenge) {
        if (codeChallenge == null || codeChallenge.length() < 43 || codeChallenge.length() > 128) {
            return false;
        }
        return codeChallenge.matches("^[A-Za-z0-9_-]+$");
    }

    private boolean isValidCodeVerifier(String codeVerifier) {
        if (codeVerifier == null || codeVerifier.length() < 43 || codeVerifier.length() > 128) {
            return false;
        }
        return codeVerifier.matches("^[A-Za-z0-9._~-]+$");
    }

    private String generateSecureCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder code = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            code.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return code.toString();
    }
}

