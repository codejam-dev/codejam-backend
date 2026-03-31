package com.codejam.infrastructure.security;

import com.codejam.auth.config.MicroserviceConfig;
import com.codejam.commons.constant.JwtScopeConstants;
import com.codejam.commons.util.JwtUtil;
import com.codejam.infrastructure.ratelimit.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Replaces the former API gateway: JWT validation, scopes, OTP rate limiting,
 * and forwarding claims as request attributes / headers for downstream controllers.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class ApiBoundaryFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "codejam.userId";

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/v1/api/auth/register",
            "/v1/api/auth/login",
            "/v1/api/auth/oauth2/authorization",
            "/v1/api/auth/oauth2/callback",
            "/v1/api/auth/oauth/exchange",
            "/v1/api/auth/resetPassword",
            "/v1/api/auth/validateResetToken"
    );

    private static final List<String> OTP_PREFIXES = List.of(
            "/v1/api/auth/generateOtp",
            "/v1/api/auth/validateOtp"
    );

    private static final String EXECUTION_PREFIX = "/v1/api/execution";

    private static boolean isExecutionAnonymous(String path) {
        return path.startsWith(EXECUTION_PREFIX + "/health")
                || path.startsWith(EXECUTION_PREFIX + "/supported-languages");
    }

    private final MicroserviceConfig microserviceConfig;
    private final RateLimiterService rateLimiterService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/actuator/") && !path.equals("/actuator/health") && !path.startsWith("/actuator/health/")) {
            writeJsonError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Actuator endpoints are restricted");
            return;
        }

        if (isPublic(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isExecutionAnonymous(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (requiresBearerGate(path)) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                writeJsonError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing or invalid Authorization header");
                return;
            }

            String token = authHeader.replaceFirst("(?i)^Bearer\\s+", "").trim();
            String secret = microserviceConfig.getJwtSecret();

            if (!JwtUtil.isTokenValid(token, secret)) {
                writeJsonError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid or expired token");
                return;
            }

            try {
                if (OTP_PREFIXES.stream().anyMatch(path::startsWith)) {
                    if (!JwtUtil.hasAnyScope(token, secret, List.of(JwtScopeConstants.OTP_GENERATE, JwtScopeConstants.OTP_VALIDATE))) {
                        writeJsonError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permissions: OTP scope required");
                        return;
                    }
                    String userId = JwtUtil.extractUserIdClaim(token, secret);
                    if (!rateLimiterService.checkRateLimit(userId)) {
                        writeJsonError(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Rate limit exceeded. Please try again later.");
                        return;
                    }
                    String email = JwtUtil.extractEmail(token, secret);
                    var wrapped = new JwtAugmentedHttpServletRequest(request, userId, email);
                    filterChain.doFilter(wrapped, response);
                    return;
                }

                if (path.startsWith(EXECUTION_PREFIX)) {
                    boolean hasApi = JwtUtil.hasScope(token, secret, JwtScopeConstants.API_READ)
                            || JwtUtil.hasScope(token, secret, JwtScopeConstants.API_WRITE);
                    if (!hasApi) {
                        writeJsonError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permissions: API access required");
                        return;
                    }
                    String userId = JwtUtil.extractUserIdClaim(token, secret);
                    request.setAttribute(ATTR_USER_ID, userId);
                    filterChain.doFilter(request, response);
                    return;
                }

                boolean hasApi = JwtUtil.hasScope(token, secret, JwtScopeConstants.API_READ)
                        || JwtUtil.hasScope(token, secret, JwtScopeConstants.API_WRITE);
                if (!hasApi) {
                    writeJsonError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permissions: API access required");
                    return;
                }
                filterChain.doFilter(request, response);
                return;

            } catch (Exception e) {
                log.warn("Token validation failed for path {}: {}", path, e.getMessage());
                writeJsonError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token validation failed");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isPublic(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /**
     * Paths that were previously enforced by the standalone API gateway (everything except public auth + root + static actuator health).
     */
    private static boolean requiresBearerGate(String path) {
        if (path.startsWith("/v1/api/")) {
            return !isPublic(path);
        }
        return false;
    }

    private static void writeJsonError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        String body = String.format(
                "{\"success\":false,\"message\":\"%s\",\"errorCode\":\"%s\",\"timestamp\":\"%s\"}",
                escapeJson(message),
                code,
                LocalDateTime.now()
        );
        response.getWriter().write(body);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
