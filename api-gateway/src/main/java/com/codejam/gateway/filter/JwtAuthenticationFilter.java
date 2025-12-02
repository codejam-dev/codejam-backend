package com.codejam.gateway.filter;

import com.codejam.gateway.service.JwtService;
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

import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtService jwtService;

    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/v1/api/auth/register",
            "/v1/api/auth/login",
            "/v1/api/auth/oauth2/authorization/google",
            "/v1/api/auth/oauth2/callback/google",
            "/v1/api/auth/oauth/exchange"
    );

    private static final List<String> ALLOWED_FOR_TEMP_TOKEN = List.of(
            "/v1/api/auth/generateOtp",
            "/v1/api/auth/validateOtp"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        // Extract token from Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        try {
            // Validate token
            if (!jwtService.isTokenValid(token)) {
                return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
            }

            // Extract user info
            String userId = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            String name = jwtService.extractName(token);
            Boolean isEnabled = jwtService.extractIsEnabled(token);

            // Check if temp token is trying to access protected endpoints
            if (Boolean.FALSE.equals(isEnabled)) {
                boolean isAllowedEndpoint = ALLOWED_FOR_TEMP_TOKEN.stream()
                        .anyMatch(path::startsWith);
                
                if (!isAllowedEndpoint) {
                    return onError(exchange, "Please verify your email", HttpStatus.FORBIDDEN);
                }
            }

            // Add user info to headers for downstream services
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userId != null ? userId : "")
                    .header("X-User-Email", email != null ? email : "")
                    .header("X-User-Name", name != null ? name : "")
                    .header("X-User-Enabled", isEnabled != null ? isEnabled.toString() : "false")
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
                java.time.LocalDateTime.now()
        );

        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(errorBody.getBytes()))
        );
    }

    @Override
    public int getOrder() {
        return -100; // High priority - run before other filters
    }
}

