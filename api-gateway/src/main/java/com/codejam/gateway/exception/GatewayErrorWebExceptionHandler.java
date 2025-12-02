package com.codejam.gateway.exception;

import com.codejam.commons.dto.BaseResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Custom error handler for Spring Cloud Gateway (WebFlux)
 * Handles 404 and other routing errors, returning JSON instead of HTML
 * This complements GatewayExceptionHandler which handles controller exceptions
 */
@Component
@Order(-2) // Higher priority than default handler (-1)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            log.warn("Response already committed, cannot handle error");
            return Mono.error(ex);
        }

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = "An error occurred";
        String path = exchange.getRequest().getPath().value();

        // Handle different types of exceptions
        if (ex instanceof NotFoundException) {
            status = HttpStatus.NOT_FOUND;
            errorMessage = "Route not found: " + path;
            log.warn("Route not found: {}", path);
        } else if (ex instanceof ResponseStatusException responseStatusException) {
            HttpStatus resolvedStatus = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            if (resolvedStatus != null) {
                status = resolvedStatus;
            }
            errorMessage = responseStatusException.getReason() != null
                    ? responseStatusException.getReason()
                    : (status != null ? status.getReasonPhrase() : "An error occurred");
            log.warn("ResponseStatusException: {} - {}", status, errorMessage);
        } else if (ex.getMessage() != null) {
            String message = ex.getMessage();
            if (message.contains("404") ||
                message.contains("NOT_FOUND") || 
                message.contains("No static resource") ||
                message.contains("No route found")) {
                status = HttpStatus.NOT_FOUND;
                errorMessage = "Route not found: " + path;
                log.warn("404 detected in message: {} - Path: {}", message, path);
            } else {
                log.error("Unhandled error in Gateway - Path: {}, Exception: {}", path, ex.getClass().getName(), ex);
            }
        } else {
            log.error("Unhandled error in Gateway - Path: {}, Exception: {}", path, ex.getClass().getName(), ex);
        }


        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("X-Error-Handler", "GatewayErrorWebExceptionHandler");

        BaseResponse errorResponse = BaseResponse.error(
                status.name(),
                errorMessage
        );

        try {
            String json = objectMapper.writeValueAsString(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Error writing error response", e);
            return Mono.error(e);
        }
    }
}

