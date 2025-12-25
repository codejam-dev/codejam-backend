package com.codejam.gateway.filter;

import com.codejam.gateway.service.JwtService;
import com.codejam.gateway.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    private final RateLimiterService rateLimiterService;

    private static final List<String> OTP_SCOPES = List.of(
            SCOPE_OTP_GENERATE,
            SCOPE_OTP_VALIDATE
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (PUBLIC_ENDPOINTS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        if (path.startsWith("/actuator/") && !path.equals("/actuator/health")) {
            return onError(exchange, "Actuator endpoints are not accessible through gateway", 
                    HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, "Missing or invalid Authorization header", 
                    HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }

        String token = authHeader.replaceFirst("(?i)^Bearer\\s+", "").trim();

        try {
            if (jwtService.isTokenNotValid(token)) {
                return onError(exchange, "Invalid or expired token", 
                        HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
            }

            String userId = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            String name = jwtService.extractName(token);
            List<String> scopes = jwtService.extractScopes(token);

            if (OTP_ENDPOINTS.stream().anyMatch(path::startsWith)) {
                if (!jwtService.hasAnyScope(token, OTP_SCOPES)) {
                    return onError(exchange, "Insufficient permissions: OTP scope required", 
                            HttpStatus.FORBIDDEN, "FORBIDDEN");
                }

                return rateLimiterService.checkRateLimit(userId)
                        .flatMap(allowed -> {
                            if (!allowed) {
                                return onError(exchange, "Rate limit exceeded. Please try again later.", 
                                        HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED");
                            }
                            return continueWithHeaders(exchange, userId, email, name, scopes, chain);
                        });
            } else {
                boolean hasApiAccess = jwtService.hasScope(token, SCOPE_API_READ) || jwtService.hasScope(token, SCOPE_API_WRITE);
                if (!hasApiAccess) {
                    return onError(exchange, "Insufficient permissions: API access required", HttpStatus.FORBIDDEN, "FORBIDDEN");
                }
                return continueWithHeaders(exchange, userId, email, name, scopes, chain);
            }

        } catch (Exception e) {
            log.error("Token validation failed for path: {}", path, e);
            return onError(exchange, "Token validation failed", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
    }

    private Mono<Void> continueWithHeaders(ServerWebExchange exchange, String userId, 
                                          String email, String name, List<String> scopes, 
                                          GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", userId != null ? userId : "")
                .header("X-User-Email", email != null ? email : "")
                .header("X-User-Name", name != null ? name : "")
                .header("X-User-Scopes", String.join(",", scopes))
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }


    private Mono<Void> onError(ServerWebExchange exchange, String message, 
                               HttpStatus status, String errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json");

        String errorBody = String.format(
                "{\"success\":false,\"message\":\"%s\",\"errorCode\":\"%s\",\"timestamp\":\"%s\"}",
                message,
                errorCode,
                LocalDateTime.now()
        );

        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorBody.getBytes())));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

