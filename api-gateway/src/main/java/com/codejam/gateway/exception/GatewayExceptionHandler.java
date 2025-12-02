package com.codejam.gateway.exception;

import com.codejam.commons.dto.BaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

/**
 * Global exception handler for API Gateway (WebFlux-based)
 * This overrides the codejam-commons handler which is servlet-based
 */
@RestControllerAdvice
public class GatewayExceptionHandler {
    Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public BaseResponse handleGenericException(
            Exception ex, 
            ServerWebExchange exchange) {
        
        log.error("Unexpected error occurred in API Gateway", ex);

        return BaseResponse.error(
                "GATEWAY_ERROR",
                "An error occurred while processing your request"
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse handleRuntimeException(
            RuntimeException ex, 
            ServerWebExchange exchange) {
        
        log.warn("Runtime exception in API Gateway: {}", ex.getMessage());

        return BaseResponse.error(
                "GATEWAY_ERROR",
                ex.getMessage() != null ? ex.getMessage() : "An error occurred"
        );
    }
}

