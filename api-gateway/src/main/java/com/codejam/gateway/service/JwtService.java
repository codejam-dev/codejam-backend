package com.codejam.gateway.service;

import com.codejam.commons.util.JwtUtil;
import com.codejam.gateway.config.MicroserviceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Gateway JWT Service - Stateless token validation only.
 * This service uses JwtUtil from commons for stateless token parsing.
 * Gateway does NOT check token blacklist (that's auth-service responsibility).
 * Gateway only validates signature and expiration, then checks scopes for authorization.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final MicroserviceConfig microserviceConfig;

    /**
     * Check if token is valid (signature and expiration only - stateless).
     * Does NOT check blacklist - that's auth-service responsibility.
     */
    public boolean isTokenNotValid(String token) {
        return !JwtUtil.isTokenValid(token, microserviceConfig.getJwtSecret());
    }

    public String extractUserId(String token) {
        return JwtUtil.extractUserId(token, microserviceConfig.getJwtSecret());
    }

    public String extractEmail(String token) {
        return JwtUtil.extractEmail(token, microserviceConfig.getJwtSecret());
    }

    public String extractName(String token) {
        return JwtUtil.extractName(token, microserviceConfig.getJwtSecret());
    }

    @Deprecated
    public Boolean extractIsEnabled(String token) {
        return JwtUtil.extractIsEnabled(token, microserviceConfig.getJwtSecret());
    }

    /**
     * Extract scopes from token.
     */
    public List<String> extractScopes(String token) {
        return JwtUtil.extractScopes(token, microserviceConfig.getJwtSecret());
    }

    /**
     * Check if token has required scope.
     */
    public boolean hasScope(String token, String requiredScope) {
        return JwtUtil.hasScope(token, microserviceConfig.getJwtSecret(), requiredScope);
    }

    /**
     * Check if token has any of the required scopes.
     */
    public boolean hasAnyScope(String token, List<String> requiredScopes) {
        return JwtUtil.hasAnyScope(token, microserviceConfig.getJwtSecret(), requiredScopes);
    }
}

