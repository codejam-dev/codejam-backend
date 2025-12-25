package com.codejam.gateway.service;

import com.codejam.commons.util.JwtUtil;
import com.codejam.gateway.config.MicroserviceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final MicroserviceConfig microserviceConfig;

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

    public List<String> extractScopes(String token) {
        return JwtUtil.extractScopes(token, microserviceConfig.getJwtSecret());
    }

    public boolean hasScope(String token, String requiredScope) {
        return JwtUtil.hasScope(token, microserviceConfig.getJwtSecret(), requiredScope);
    }

    public boolean hasAnyScope(String token, List<String> requiredScopes) {
        return JwtUtil.hasAnyScope(token, microserviceConfig.getJwtSecret(), requiredScopes);
    }
}

