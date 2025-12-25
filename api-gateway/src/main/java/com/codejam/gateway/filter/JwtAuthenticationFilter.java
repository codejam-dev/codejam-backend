package com.codejam.gateway.filter;

import com.codejam.gateway.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static com.codejam.gateway.utils.Constants.*;

@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;

    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/v1/api/auth/register",
            "/v1/api/auth/login",
            "/v1/api/auth/oauth2/authorization/google",
            "/v1/api/auth/oauth2/callback/google",
            "/v1/api/auth/oauth/exchange",
            "/v1/api/auth/resetPassword",
            "/v1/api/auth/validateResetToken"
    );

    // Scopes required for OTP endpoints
    private static final List<String> OTP_ENDPOINTS = List.of(
            "/v1/api/auth/generateOtp",
            "/v1/api/auth/validateOtp"
    );
    
    private static final List<String> OTP_SCOPES = List.of(
            SCOPE_OTP_GENERATE,
            SCOPE_OTP_VALIDATE
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        if (path.startsWith("/actuator/") && !path.equals("/actuator/health")) {
            return onError(exchange, "Actuator endpoints are not accessible through gateway", HttpStatus.FORBIDDEN);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }

       String token = authHeader.replaceFirst("(?i)^Bearer\\s+", "").trim();

        try {
            if (jwtService.isTokenNotValid(token)) {
                return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
            }

            if("/v1/api/auth/logout".equalsIgnoreCase(path)) {
                return chain.filter(exchange);
            }

            String userId = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            String name = jwtService.extractName(token);
            List<String> scopes = jwtService.extractScopes(token);

            if (OTP_ENDPOINTS.stream().anyMatch(path::startsWith)) {
                if (!jwtService.hasAnyScope(token, OTP_SCOPES)) {
                    return onError(exchange, "Insufficient permissions: OTP scope required", HttpStatus.FORBIDDEN);
                }
            } else {
                if (!jwtService.hasScope(token, SCOPE_API_READ)) {
                    return onError(exchange, "Insufficient permissions: API access required", HttpStatus.FORBIDDEN);
                }
            }

            // Add user info to headers for downstream services
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userId != null ? userId : "")
                    .header("X-User-Email", email != null ? email : "")
                    .header("X-User-Name", name != null ? name : "")
                    .header("X-User-Scopes", String.join(",", scopes))
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (Exception e) {
            return onError(exchange, "Token validation failed: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
        }
    }

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json");

        String errorBody = String.format(
                "{\"success\":false,\"message\":\"%s\",\"errorCode\":\"UNAUTHORIZED\",\"timestamp\":\"%s\"}",
                message,
                LocalDateTime.now()
        );

        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorBody.getBytes())));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

