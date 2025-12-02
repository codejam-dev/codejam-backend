package com.codejam.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.codejam.commons.dto.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Global filter to catch unmatched routes and return JSON 404 instead of HTML
 * This runs after route matching to catch routes that don't match any configured route
 */
@Component
public class RouteNotFoundFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper = new ObjectMapper();
    Logger log = LoggerFactory.getLogger(RouteNotFoundFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // Check if this is an API path that should be routed
        if (path.startsWith("/v1/api/")) {
            // Let it proceed - if route doesn't match, ErrorWebExceptionHandler will catch it
            return chain.filter(exchange).onErrorResume(throwable -> {
                // If route matching fails, handle it here
                if (throwable.getMessage() != null && 
                    (throwable.getMessage().contains("404") || 
                     throwable.getMessage().contains("No static resource") ||
                     throwable.getMessage().contains("No route found"))) {
                    return handleRouteNotFound(exchange, path);
                }
                return Mono.error(throwable);
            });
        }
        
        return chain.filter(exchange);
    }

    private Mono<Void> handleRouteNotFound(ServerWebExchange exchange, String path) {
        ServerHttpResponse response = exchange.getResponse();
        
        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(HttpStatus.NOT_FOUND);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        BaseResponse errorResponse = BaseResponse.error(
            "NOT_FOUND",
            "Route not found: " + path
        );

        try {
            String json = objectMapper.writeValueAsString(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Error writing 404 response", e);
            return Mono.error(e);
        }
    }

    @Override
    public int getOrder() {
        // Run after route matching (default is 0, so use a higher number)
        return Ordered.LOWEST_PRECEDENCE;
    }
}

