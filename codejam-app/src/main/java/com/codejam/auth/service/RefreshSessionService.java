package com.codejam.auth.service;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.auth.dto.response.SessionTokens;
import com.codejam.auth.model.RefreshTokenMetadata;
import com.codejam.auth.model.User;
import com.codejam.auth.repository.UserRepository;
import com.codejam.commons.exception.CustomException;
import com.codejam.commons.service.RedisService;
import com.codejam.commons.util.proxyUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshSessionService {

    private final JwtService jwtService;
    private final RedisService redisService;
    private final proxyUtils proxyUtils;
    private final MicroserviceConfig microserviceConfig;
    private final UserRepository userRepository;

    public SessionTokens createFreshSession(User user, String deviceId) {
        removeExistingSessionForDevice(user.getUserId(), deviceId);

        String accessToken = jwtService.generateAccessToken(user, deviceId);
        String refreshToken = jwtService.generateRefreshToken(user, deviceId, null);
        String jti = jwtService.extractRefreshJti(refreshToken);
        long ttlSeconds = refreshTtlSeconds();

        RefreshTokenMetadata meta = RefreshTokenMetadata.builder()
                .userId(user.getUserId())
                .deviceId(deviceId)
                .revoked(false)
                .expiresAt(System.currentTimeMillis() + microserviceConfig.getRefreshTokenExpiryMs())
                .build();

        redisService.set(refreshRedisKey(jti), meta.toJson(), ttlSeconds);
        redisService.set(sessionRedisKey(user.getUserId(), deviceId), jti, ttlSeconds);
        redisService.setAdd(userSessionsRedisKey(user.getUserId()), deviceId);

        return new SessionTokens(accessToken, refreshToken);
    }

    public SessionTokens rotateRefreshToken(String refreshToken) {
        if (!jwtService.isRefreshTokenWellFormed(refreshToken)) {
            throw new CustomException("INVALID_REFRESH", "Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }

        Claims claims = jwtService.parseRefreshTokenClaims(refreshToken);
        String jti = claims.getId();
        String jwtUserId = claims.get("userId", String.class);
        String jwtDeviceId = claims.get("deviceId", String.class);

        String raw = redisService.get(refreshRedisKey(jti));
        if (raw == null) {
            throw new CustomException("INVALID_REFRESH", "Refresh session not found or expired", HttpStatus.UNAUTHORIZED);
        }

        RefreshTokenMetadata existing = RefreshTokenMetadata.fromJson(raw);
        if (existing.isRevoked()) {
            log.warn("Refresh token reuse detected for userId={}", existing.getUserId());
            revokeAllSessionsForUser(existing.getUserId());
            throw new CustomException("REFRESH_REUSED", "Refresh token reuse detected; all sessions invalidated", HttpStatus.UNAUTHORIZED);
        }

        if (!existing.getUserId().equals(jwtUserId) || !existing.getDeviceId().equals(jwtDeviceId)) {
            throw new CustomException("INVALID_REFRESH", "Refresh token mismatch", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByUserId(existing.getUserId())
                .orElseThrow(() -> new CustomException("USER_NOT_FOUND", "User not found", HttpStatus.UNAUTHORIZED));
        if (!user.isEnabled()) {
            revokeAllSessionsForUser(user.getUserId());
            throw new CustomException("USER_DISABLED", "Account is not active", HttpStatus.UNAUTHORIZED);
        }

        Long rem = redisService.getExpire(refreshRedisKey(jti));
        long remaining = (rem == null || rem <= 0) ? refreshTtlSeconds() : rem;

        RefreshTokenMetadata revoked = RefreshTokenMetadata.builder()
                .userId(existing.getUserId())
                .deviceId(existing.getDeviceId())
                .revoked(true)
                .expiresAt(existing.getExpiresAt())
                .parentJti(existing.getParentJti())
                .build();
        redisService.set(refreshRedisKey(jti), revoked.toJson(), remaining);

        String newRefresh = jwtService.generateRefreshToken(user, existing.getDeviceId(), jti);
        String newJti = jwtService.extractRefreshJti(newRefresh);

        RefreshTokenMetadata fresh = RefreshTokenMetadata.builder()
                .userId(existing.getUserId())
                .deviceId(existing.getDeviceId())
                .revoked(false)
                .expiresAt(System.currentTimeMillis() + microserviceConfig.getRefreshTokenExpiryMs())
                .parentJti(jti)
                .build();

        long newTtl = refreshTtlSeconds();
        redisService.set(refreshRedisKey(newJti), fresh.toJson(), newTtl);
        redisService.set(sessionRedisKey(existing.getUserId(), existing.getDeviceId()), newJti, newTtl);

        String accessToken = jwtService.generateAccessToken(user, existing.getDeviceId());
        return new SessionTokens(accessToken, newRefresh);
    }

    public void logoutWithRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            if (!jwtService.isRefreshTokenWellFormed(refreshToken)) {
                return;
            }
            String jti = jwtService.extractRefreshJti(refreshToken);
            String jwtUserId = jwtService.extractRefreshUserId(refreshToken);
            String jwtDeviceId = jwtService.extractRefreshDeviceId(refreshToken);

            String raw = redisService.get(refreshRedisKey(jti));
            if (raw == null) {
                return;
            }
            RefreshTokenMetadata meta = RefreshTokenMetadata.fromJson(raw);
            if (!meta.getUserId().equals(jwtUserId) || !meta.getDeviceId().equals(jwtDeviceId)) {
                return;
            }

            redisService.delete(refreshRedisKey(jti));
            redisService.delete(sessionRedisKey(meta.getUserId(), meta.getDeviceId()));
            redisService.setRemove(userSessionsRedisKey(meta.getUserId()), meta.getDeviceId());
        } catch (Exception e) {
            log.debug("Logout refresh cleanup skipped: {}", e.getMessage());
        }
    }

    public void revokeAllSessionsForUser(String userId) {
        String usKey = userSessionsRedisKey(userId);
        Set<String> devices = redisService.setMembers(usKey);
        if (devices == null || devices.isEmpty()) {
            redisService.deleteSet(usKey);
            return;
        }
        for (String deviceId : devices) {
            String sKey = sessionRedisKey(userId, deviceId);
            String jti = redisService.get(sKey);
            if (jti != null) {
                redisService.delete(refreshRedisKey(jti));
            }
            redisService.delete(sKey);
        }
        redisService.deleteSet(usKey);
    }

    private void removeExistingSessionForDevice(String userId, String deviceId) {
        String sKey = sessionRedisKey(userId, deviceId);
        String oldJti = redisService.get(sKey);
        if (oldJti != null) {
            redisService.delete(refreshRedisKey(oldJti));
            redisService.delete(sKey);
        }
        redisService.setRemove(userSessionsRedisKey(userId), deviceId);
    }

    private long refreshTtlSeconds() {
        return Math.max(1L, microserviceConfig.getRefreshTokenExpiryMs() / 1000L);
    }

    private String refreshRedisKey(String jti) {
        return proxyUtils.generateRedisKey("refresh_token_jti", jti);
    }

    private String sessionRedisKey(String userId, String deviceId) {
        return proxyUtils.generateRedisKey("session", userId, deviceId);
    }

    private String userSessionsRedisKey(String userId) {
        return proxyUtils.generateRedisKey("userSessions", userId);
    }
}
