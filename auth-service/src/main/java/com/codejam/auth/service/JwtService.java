package com.codejam.auth.service;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.auth.model.User;
import com.codejam.commons.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static io.jsonwebtoken.Jwts.builder;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final MicroserviceConfig microserviceConfig;

    private static final long TEMP_TOKEN_EXPIRATION = 15 * 60 * 1000;
    
    // Scope constants
    public static final String SCOPE_API_READ = "api:read";
    public static final String SCOPE_API_WRITE = "api:write";
    public static final String SCOPE_OTP_GENERATE = "otp:generate";
    public static final String SCOPE_OTP_VALIDATE = "otp:validate";

    // Delegate to JwtUtil for parsing
    public String extractEmail(String token) {
        return JwtUtil.extractEmail(token, microserviceConfig.getJwtSecret());
    }

    public String extractUserId(String token) {
        return JwtUtil.extractUserId(token, microserviceConfig.getJwtSecret());
    }

    @Deprecated
    public Boolean extractIsEnabled(String token) {
        return JwtUtil.extractIsEnabled(token, microserviceConfig.getJwtSecret());
    }

    public List<String> extractScopes(String token) {
        return JwtUtil.extractScopes(token, microserviceConfig.getJwtSecret());
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = JwtUtil.parseToken(token, microserviceConfig.getJwtSecret());
        return claimsResolver.apply(claims);
    }

    public String generateToken(User user) {
        return buildToken(user, microserviceConfig.getJwtExpiration());
    }

    public String generateTempToken(User user) {
        return buildToken(user, TEMP_TOKEN_EXPIRATION);
    }

    // Removed generateFullToken - use generateToken() instead

    private String buildToken(User user, long expiration, List<String> scopes) {
        Map<String, Object> claimsMap = new java.util.HashMap<>();
        claimsMap.put("userId", user.getUserId());
        claimsMap.put("email", user.getEmail());
        claimsMap.put("name", user.getName());
        claimsMap.put("isEnabled", user.isEnabled());
        claimsMap.put("scope", scopes);
        
        return builder()
                .id(UUID.randomUUID().toString())
                .claims(claimsMap)
                .subject(user.getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }
    
    /**
     * Build token with default scopes based on user enabled status.
     */
    private String buildToken(User user, long expiration) {
        List<String> scopes = determineScopes(user);
        return buildToken(user, expiration, scopes);
    }
    
    /**
     * Determine scopes based on user state.
     * - Temp tokens (unverified users): Only OTP scopes
     * - Full tokens (verified users): Full API access
     */
    private List<String> determineScopes(User user) {
        List<String> scopes = new ArrayList<>();
        if (user.isEnabled()) {
            scopes.add(SCOPE_API_READ);
            scopes.add(SCOPE_API_WRITE);
        } else {
            scopes.add(SCOPE_OTP_GENERATE);
            scopes.add(SCOPE_OTP_VALIDATE);
        }
        return scopes;
    }

    public boolean isTokenValid(String token, User user) {
        final String email = extractEmail(token);
        return (email.equals(user.getEmail())) && JwtUtil.isTokenValid(token, microserviceConfig.getJwtSecret());
    }

    // Simple token validation - checks if token is valid (not expired and properly signed)
    public boolean isTokenValid(String token) {
        return JwtUtil.isTokenValid(token, microserviceConfig.getJwtSecret());
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(microserviceConfig.getJwtSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    // Remove generateFullToken - it's identical to generateToken
    // Use generateToken() for full tokens

    /**
     * Extract expiration time in seconds from the token
     * @param token the JWT token
     * @return expiration time in seconds
     */
    public long extractExpirationTime(String token) {
        return JwtUtil.extractExpirationTime(token, microserviceConfig.getJwtSecret());
    }
}
