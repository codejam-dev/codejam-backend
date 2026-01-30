package com.codejam.gateway.filter;

import com.codejam.gateway.service.JwtService;
import com.codejam.gateway.service.RateLimiterService;
import com.codejam.gateway.utils.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.codejam.gateway.utils.Constants.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> OTP_SCOPES = List.of(
            SCOPE_OTP_GENERATE,
            SCOPE_OTP_VALIDATE
    );

    private final JwtService jwtService;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (PUBLIC_ENDPOINTS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/actuator/") && !path.equals("/actuator/health") && !path.startsWith("/actuator/health/")) {
            sendError(response, HttpStatus.FORBIDDEN, "Actuator endpoints are not accessible", "FORBIDDEN");
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header", "UNAUTHORIZED");
            return;
        }

        String token = authHeader.replaceFirst("(?i)^Bearer\\s+", "").trim();

        try {
            if (jwtService.isTokenNotValid(token)) {
                log.warn("Token validation failed for path: {} - token is invalid or expired", path);
                sendError(response, HttpStatus.UNAUTHORIZED, "Invalid or expired token", "UNAUTHORIZED");
                return;
            }

            String userId = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            String name = jwtService.extractName(token);
            List<String> scopes = jwtService.extractScopes(token);

            if (OTP_ENDPOINTS.stream().anyMatch(path::startsWith)) {
                if (!jwtService.hasAnyScope(token, OTP_SCOPES)) {
                    sendError(response, HttpStatus.FORBIDDEN, "Insufficient permissions: OTP scope required", "FORBIDDEN");
                    return;
                }
                if (!rateLimiterService.checkRateLimit(userId)) {
                    sendError(response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Please try again later.", "RATE_LIMIT_EXCEEDED");
                    return;
                }
            } else {
                boolean hasApiAccess = jwtService.hasScope(token, SCOPE_API_READ) || jwtService.hasScope(token, SCOPE_API_WRITE);
                if (!hasApiAccess) {
                    sendError(response, HttpStatus.FORBIDDEN, "Insufficient permissions: API access required", "FORBIDDEN");
                    return;
                }
            }

            Boolean isEnabled;
            try {
                isEnabled = jwtService.extractIsEnabled(token);
            } catch (Exception e) {
                isEnabled = Boolean.TRUE;
            }
            boolean enabled = Boolean.TRUE.equals(isEnabled);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                List<SimpleGrantedAuthority> authorities = enabled
                        ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                        : Collections.emptyList();
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userId, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            HttpServletRequest wrapped = new UserHeadersRequestWrapper(
                    request, userId, email, name, scopes != null ? String.join(",", scopes) : "");
            filterChain.doFilter(wrapped, response);
        } catch (Exception e) {
            log.error("Token validation failed for path: {} - {} - {}", path, e.getClass().getSimpleName(), e.getMessage(), e);
            sendError(response, HttpStatus.UNAUTHORIZED, "Token validation failed: " + e.getMessage(), "UNAUTHORIZED");
        }
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String message, String errorCode) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
                "success", false,
                "message", message,
                "errorCode", errorCode,
                "timestamp", LocalDateTime.now().toString()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
