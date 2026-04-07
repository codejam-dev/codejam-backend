package com.codejam.auth.service;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.auth.model.User;
import com.codejam.auth.util.AuthProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private MicroserviceConfig microserviceConfig;

    @InjectMocks
    private JwtService jwtService;

    private String secretB64;

    @BeforeEach
    void setUp() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        secretB64 = Base64.getEncoder().encodeToString(raw);
        lenient().when(microserviceConfig.getJwtSecret()).thenReturn(secretB64);
        lenient().when(microserviceConfig.getJwtRefreshSecret()).thenReturn(secretB64);
        lenient().when(microserviceConfig.getAccessTokenExpiryMs()).thenReturn(900_000L);
        lenient().when(microserviceConfig.getRefreshTokenExpiryMs()).thenReturn(604_800_000L);
    }

    @Test
    void accessToken_containsTokenUseAccess_deviceId_andScopes() {
        User user = User.builder()
                .userId("uid-1")
                .email("a@b.c")
                .name("N")
                .password("x")
                .provider(AuthProvider.LOCAL)
                .enabled(true)
                .build();

        String jwt = jwtService.generateAccessToken(user, "dev-1");

        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretB64)))
                .build()
                .parseSignedClaims(jwt)
                .getPayload();

        assertThat(claims.get("token_use", String.class)).isEqualTo(JwtService.TOKEN_USE_ACCESS);
        assertThat(claims.get("deviceId", String.class)).isEqualTo("dev-1");
        assertThat(claims.getSubject()).isEqualTo("a@b.c");
        assertThat(claims.get("userId", String.class)).isEqualTo("uid-1");
        assertThat(jwtService.isTokenValid(jwt)).isTrue();
    }

    @Test
    void refreshToken_containsTokenUseRefresh() {
        User user = User.builder()
                .userId("uid-2")
                .email("x@y.z")
                .name("R")
                .password("p")
                .provider(AuthProvider.LOCAL)
                .enabled(true)
                .build();

        String jwt = jwtService.generateRefreshToken(user, "dev-2", null);

        assertThat(jwtService.isRefreshTokenWellFormed(jwt)).isTrue();
        var claims = jwtService.parseRefreshTokenClaims(jwt);
        assertThat(claims.get("token_use", String.class)).isEqualTo(JwtService.TOKEN_USE_REFRESH);
        assertThat(claims.get("userId", String.class)).isEqualTo("uid-2");
        assertThat(claims.get("deviceId", String.class)).isEqualTo("dev-2");
    }
}
