package com.codejam.auth.service;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.auth.repository.UserRepository;
import com.codejam.commons.exception.CustomException;
import com.codejam.commons.service.RedisService;
import com.codejam.commons.util.proxyUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private RedisService redisService;

    @Mock
    private proxyUtils proxyUtils;

    @Mock
    private MicroserviceConfig microserviceConfig;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshSessionService refreshSessionService;

    @Test
    void rotate_whenRedisMarkedRevoked_revokesAllUserSessions() {
        String jwt = "header.payload.sig";
        when(jwtService.isRefreshTokenWellFormed(jwt)).thenReturn(true);
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getId()).thenReturn("jti-revoked");
        when(claims.get("userId", String.class)).thenReturn("user-a");
        when(claims.get("deviceId", String.class)).thenReturn("device-1");
        when(jwtService.parseRefreshTokenClaims(jwt)).thenReturn(claims);

        when(proxyUtils.generateRedisKey(eq("refresh_token_jti"), eq("jti-revoked")))
                .thenReturn("refresh_token_jti_jti-revoked");
        when(proxyUtils.generateRedisKey(eq("userSessions"), eq("user-a")))
                .thenReturn("userSessions_user-a");

        String revokedJson = "{\"userId\":\"user-a\",\"deviceId\":\"device-1\",\"revoked\":true,\"expiresAt\":9999999999999}";
        when(redisService.get("refresh_token_jti_jti-revoked")).thenReturn(revokedJson);
        when(redisService.setMembers("userSessions_user-a")).thenReturn(Collections.emptySet());

        assertThatThrownBy(() -> refreshSessionService.rotateRefreshToken(jwt))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorType", "REFRESH_REUSED");

        verify(redisService).deleteSet("userSessions_user-a");
    }
}
