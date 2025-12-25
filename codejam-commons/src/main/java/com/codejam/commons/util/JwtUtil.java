package com.codejam.commons.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

@Slf4j
public class JwtUtil {

    /**
     * Parse and validate JWT token (signature and expiration only).
     * This is stateless validation - does not check blacklist.
     * 
     * @param token JWT token string
     * @param jwtSecret Base64-encoded JWT secret
     * @return Claims if token is valid
     * @throws ExpiredJwtException if token is expired
     * @throws JwtException if token is invalid or malformed
     */
    public static Claims parseToken(String token, String jwtSecret) {
        try {
            SecretKey key = getSignInKey(jwtSecret);
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new JwtException("Invalid token format: " + e.getMessage(), e);
        } catch (JwtException e) {
            throw new JwtException("Invalid token: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new JwtException("Token parsing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if token is expired (stateless check only).
     * 
     * @param token JWT token string
     * @param jwtSecret Base64-encoded JWT secret
     * @return true if token is expired, false otherwise
     */
    public static boolean isTokenExpired(String token, String jwtSecret) {
        try {
            Claims claims = parseToken(token, jwtSecret);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return true; // Invalid token treated as expired
        }
    }

    /**
     * Validate token signature and expiration (stateless validation).
     * Does NOT check blacklist - that's service-specific.
     * 
     * @param token JWT token string
     * @param jwtSecret Base64-encoded JWT secret
     * @return true if token is valid (signed correctly and not expired), false otherwise
     */
    public static boolean isTokenValid(String token, String jwtSecret) {
        try {
            return !isTokenExpired(token, jwtSecret);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract claim from token.
     * 
     * @param token JWT token string
     * @param jwtSecret Base64-encoded JWT secret
     * @param claimsResolver Function to extract specific claim
     * @return Extracted claim value
     */
    public static <T> T extractClaim(String token, String jwtSecret, Function<Claims, T> claimsResolver) {
        Claims claims = parseToken(token, jwtSecret);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract user ID from token (subject claim).
     */
    public static String extractUserId(String token, String jwtSecret) {
        return extractClaim(token, jwtSecret, Claims::getSubject);
    }

    /**
     * Extract email from token.
     */
    public static String extractEmail(String token, String jwtSecret) {
        return extractClaim(token, jwtSecret, claims -> claims.get("email", String.class));
    }

    /**
     * Extract name from token.
     */
    public static String extractName(String token, String jwtSecret) {
        return extractClaim(token, jwtSecret, claims -> claims.get("name", String.class));
    }

    /**
     * Extract isEnabled from token (for backward compatibility).
     * @deprecated Use scope-based authorization instead
     */
    @Deprecated
    public static Boolean extractIsEnabled(String token, String jwtSecret) {
        return extractClaim(token, jwtSecret, claims -> claims.get("isEnabled", Boolean.class));
    }

    /**
     * Extract scopes from token.
     * Scopes are stored as a list in the "scope" claim.
     * 
     * @param token JWT token string
     * @param jwtSecret Base64-encoded JWT secret
     * @return List of scopes, or empty list if not present
     */
    @SuppressWarnings("unchecked")
    public static List<String> extractScopes(String token, String jwtSecret) {
        return extractClaim(token, jwtSecret, claims -> {
            Object scopeObj = claims.get("scope");
            if (scopeObj instanceof List) {
                return (List<String>) scopeObj;
            }
            return List.of();
        });
    }

    /**
     * Check if token has required scope.
     * 
     * @param token JWT token string
     * @param jwtSecret Base64-encoded JWT secret
     * @param requiredScope Required scope (e.g., "otp:generate", "api:read")
     * @return true if token has the required scope, false otherwise
     */
    public static boolean hasScope(String token, String jwtSecret, String requiredScope) {
        List<String> scopes = extractScopes(token, jwtSecret);
        return scopes.contains(requiredScope);
    }

    /**
     * Check if token has any of the required scopes.
     * 
     * @param token JWT token string
     * @param jwtSecret Base64-encoded JWT secret
     * @param requiredScopes List of required scopes (OR logic)
     * @return true if token has at least one of the required scopes, false otherwise
     */
    public static boolean hasAnyScope(String token, String jwtSecret, List<String> requiredScopes) {
        List<String> tokenScopes = extractScopes(token, jwtSecret);
        return requiredScopes.stream().anyMatch(tokenScopes::contains);
    }

    /**
     * Extract expiration time in seconds from token.
     * 
     * @param token JWT token string
     * @param jwtSecret Base64-encoded JWT secret
     * @return Expiration time in seconds (negative if already expired)
     */
    public static long extractExpirationTime(String token, String jwtSecret) {
        try {
            Date expiration = extractClaim(token, jwtSecret, Claims::getExpiration);
            Date now = new Date();
            return (expiration.getTime() - now.getTime()) / 1000;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Get signing key from base64-encoded secret.
     */
    private static SecretKey getSignInKey(String jwtSecret) {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid JWT secret format. Must be base64-encoded.", e);
        }
    }
}

