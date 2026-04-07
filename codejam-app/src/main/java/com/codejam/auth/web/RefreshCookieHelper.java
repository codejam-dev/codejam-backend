package com.codejam.auth.web;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.auth.util.Constants;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshCookieHelper {

    private final MicroserviceConfig microserviceConfig;

    public void writeRefreshCookie(HttpServletResponse response, String refreshJwt) {
        long maxAgeSec = Math.max(1L, microserviceConfig.getRefreshTokenExpiryMs() / 1000L);
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(Constants.COOKIE_REFRESH_TOKEN, refreshJwt)
                .path(Constants.REFRESH_COOKIE_PATH)
                .httpOnly(true)
                .secure(microserviceConfig.isRefreshCookieSecure())
                .maxAge(Duration.ofSeconds(maxAgeSec));
        String sameSite = microserviceConfig.getRefreshCookieSameSite();
        if (sameSite != null && !sameSite.isBlank()) {
            b.sameSite(sameSite);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, b.build().toString());
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(Constants.COOKIE_REFRESH_TOKEN, "")
                .path(Constants.REFRESH_COOKIE_PATH)
                .httpOnly(true)
                .secure(microserviceConfig.isRefreshCookieSecure())
                .maxAge(Duration.ZERO);
        String sameSite = microserviceConfig.getRefreshCookieSameSite();
        if (sameSite != null && !sameSite.isBlank()) {
            b.sameSite(sameSite);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, b.build().toString());
    }
}
