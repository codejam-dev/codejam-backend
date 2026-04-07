package com.codejam.auth.service;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.auth.model.User;
import com.codejam.commons.constant.JwtScopeConstants;
import com.codejam.commons.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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

    public static final String TOKEN_USE_ACCESS = "access";
    public static final String TOKEN_USE_REFRESH = "refresh";

    public String extractEmail(String token) {
        return JwtUtil.extractEmail(token, microserviceConfig.getJwtSecret());
    }

    public String extractUserId(String token) {
        return JwtUtil.extractUserIdFromClaims(token, microserviceConfig.getJwtSecret());
    }

    @Deprecated
    public Boolean extractIsEnabled(String token) {
        return JwtUtil.extractIsEnabled(token, microserviceConfig.getJwtSecret());
    }

    public List<String> extractScopes(String token) {
        return JwtUtil.extractScopes(token, microserviceConfig.getJwtSecret());
    }

    public String extractJti(String token) {
        return JwtUtil.extractJti(token, microserviceConfig.getJwtSecret());
    }

    public String extractDeviceIdFromAccessToken(String token) {
        return JwtUtil.extractDeviceId(token, microserviceConfig.getJwtSecret());
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = JwtUtil.parseToken(token, microserviceConfig.getJwtSecret());
        return claimsResolver.apply(claims);
    }

    /**
     * Short-lived access JWT for verified users (API boundary validates with jwt.secret).
     */
    public String generateAccessToken(User user, String deviceId) {
        List<String> scopes = determineScopes(user);
        return buildAccessToken(user, deviceId, microserviceConfig.getAccessTokenExpiryMs(), scopes);
    }

    /**
     * Temp access JWT for unverified users (OTP flows only at API boundary).
     */
    public String generateTempToken(User user) {
        List<String> scopes = determineScopes(user);
        return buildAccessToken(user, null, microserviceConfig.getAccessTokenExpiryMs(), scopes);
    }

    /**
     * Refresh JWT (signed with jwt.refresh.secret; not accepted for API routes).
     */
    public String generateRefreshToken(User user, String deviceId, String parentJti) {
        String jti = UUID.randomUUID().toString();
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("deviceId", deviceId);
        claims.put("token_use", TOKEN_USE_REFRESH);
        if (parentJti != null && !parentJti.isEmpty()) {
            claims.put("parentJti", parentJti);
        }

        return builder()
                .id(jti)
                .claims(claims)
                .subject(user.getUserId())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + microserviceConfig.getRefreshTokenExpiryMs()))
                .signWith(getRefreshSignInKey())
                .compact();
    }

    public boolean isRefreshTokenWellFormed(String token) {
        try {
            Claims c = JwtUtil.parseToken(token, microserviceConfig.getJwtRefreshSecret());
            return TOKEN_USE_REFRESH.equals(c.get("token_use", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public Claims parseRefreshTokenClaims(String token) {
        return JwtUtil.parseToken(token, microserviceConfig.getJwtRefreshSecret());
    }

    public String extractRefreshJti(String token) {
        return JwtUtil.extractJti(token, microserviceConfig.getJwtRefreshSecret());
    }

    public String extractRefreshUserId(String token) {
        return JwtUtil.parseToken(token, microserviceConfig.getJwtRefreshSecret()).get("userId", String.class);
    }

    public String extractRefreshDeviceId(String token) {
        return JwtUtil.parseToken(token, microserviceConfig.getJwtRefreshSecret()).get("deviceId", String.class);
    }

    /**
     * @deprecated Use {@link #generateAccessToken(User, String)} for verified sessions.
     */
    @Deprecated
    public String generateToken(User user) {
        return generateAccessToken(user, null);
    }

    private String buildAccessToken(User user, String deviceId, long expirationMs, List<String> scopes) {
        String jti = UUID.randomUUID().toString();
        Map<String, Object> claimsMap = new java.util.HashMap<>();
        claimsMap.put("userId", user.getUserId());
        claimsMap.put("email", user.getEmail());
        claimsMap.put("name", user.getName());
        claimsMap.put("isEnabled", user.isEnabled());
        claimsMap.put("scope", scopes);
        claimsMap.put("token_use", TOKEN_USE_ACCESS);
        if (deviceId != null && !deviceId.isEmpty()) {
            claimsMap.put("deviceId", deviceId);
        }

        return builder()
                .id(jti)
                .claims(claimsMap)
                .subject(user.getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSignInKey())
                .compact();
    }

    private List<String> determineScopes(User user) {
        List<String> scopes = new ArrayList<>();
        if (user.isEnabled()) {
            scopes.add(JwtScopeConstants.API_READ);
            scopes.add(JwtScopeConstants.API_WRITE);
        } else {
            scopes.add(JwtScopeConstants.OTP_GENERATE);
            scopes.add(JwtScopeConstants.OTP_VALIDATE);
        }
        return scopes;
    }

    public boolean isTokenValid(String token, User user) {
        final String email = extractEmail(token);
        return (email.equals(user.getEmail())) && JwtUtil.isTokenValid(token, microserviceConfig.getJwtSecret());
    }

    public boolean isTokenValid(String token) {
        try {
            Claims c = JwtUtil.parseToken(token, microserviceConfig.getJwtSecret());
            String use = c.get("token_use", String.class);
            if (use != null && !TOKEN_USE_ACCESS.equals(use)) {
                return false;
            }
            return JwtUtil.isTokenValid(token, microserviceConfig.getJwtSecret());
        } catch (JwtException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(microserviceConfig.getJwtSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey getRefreshSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(microserviceConfig.getJwtRefreshSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public long extractExpirationTime(String token) {
        return JwtUtil.extractExpirationTime(token, microserviceConfig.getJwtSecret());
    }

    public long extractRefreshExpirationTimeSeconds(String token) {
        return JwtUtil.extractExpirationTime(token, microserviceConfig.getJwtRefreshSecret());
    }
}
