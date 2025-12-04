package com.codejam.auth.service;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.auth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

import static io.jsonwebtoken.Jwts.builder;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final MicroserviceConfig microserviceConfig;

    private static final long TEMP_TOKEN_EXPIRATION = 15 * 60 * 1000;

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Boolean extractIsEnabled(String token) {
        return extractClaim(token, claims -> claims.get("isEnabled", Boolean.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(User user) {
        return buildToken(user, microserviceConfig.getJwtExpiration());
    }

    public String generateTempToken(User user) {
        return buildToken(user, TEMP_TOKEN_EXPIRATION);
    }

    public String generateFullToken(User user) {
        return buildToken(user, microserviceConfig.getJwtExpiration());
    }

    private String buildToken(User user, long expiration) {
        Map<String, Object> claims = Map.of(
                "userId", user.getUserId(),
                "email", user.getEmail(),
                "name", user.getName(),
                "isEnabled", user.isEnabled()
        );
        return builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, User user) {
        final String email = extractEmail(token);
        return (email.equals(user.getEmail())) && !isTokenExpired(token);
    }

    // Simple token validation - checks if token is valid (not expired and properly signed)
    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(microserviceConfig.getJwtSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Extract expiration time in seconds from the token
     * @param token the JWT token
     * @return expiration time in seconds
     */
    public long extractExpirationTime(String token) {
        Date expiration = extractExpiration(token);
        Date now = new Date();
        return (expiration.getTime() - now.getTime()) / 1000;
    }
}
