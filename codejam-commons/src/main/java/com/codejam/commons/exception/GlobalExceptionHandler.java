package com.codejam.commons.exception;

import com.codejam.commons.dto.BaseResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<BaseResponse> handleCustomException(CustomException ex, HttpServletRequest request) {
        log.warn("Custom exception: {} - {}", ex.getErrorType(), ex.getCustomMessage());

        BaseResponse response = BaseResponse.error(
                ex.getCustomMessage() != null ? ex.getCustomMessage() : "An error occurred",
                ex.getHttpStatus().name()
        );

        return new ResponseEntity<>(response, ex.getHttpStatus());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<BaseResponse> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());

        BaseResponse response = BaseResponse.error(
                "Invalid username or password",
                "AUTHENTICATION_FAILED"
        );

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<BaseResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication exception: {}", ex.getMessage());

        BaseResponse response = BaseResponse.error(
                "Authentication failed",
                "AUTHENTICATION_FAILED"
        );

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse> handleTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch for parameter: {}", ex.getMessage());

        BaseResponse response = BaseResponse.error(
                "Invalid value for parameter '" + ex.getName() + "'. Expected " + ex.getRequiredType().getSimpleName(),
                "INVALID_PARAMETER_TYPE"
        );

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<BaseResponse> handleMissingHeaderException(MissingRequestHeaderException ex, HttpServletRequest request) {
        log.warn("Missing required header: {}", ex.getMessage());

        BaseResponse response = BaseResponse.error(
                "Required header '" + ex.getHeaderName() + "' is missing",
                "MISSING_HEADER"
        );

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.warn("No handler found for: {} {}", ex.getHttpMethod(), ex.getRequestURL());

        BaseResponse response = BaseResponse.error(
                "Endpoint not found: " + ex.getRequestURL(),
                "NOT_FOUND"
        );

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BaseResponse> handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());

        String errorMessage = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));

        BaseResponse response = BaseResponse.error(
                errorMessage,
                "VALIDATION_ERROR"
        );

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.warn("Method argument validation failed: {}", ex.getMessage());

        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        BaseResponse response = BaseResponse.error(
                errorMessage,
                "VALIDATION_ERROR"
        );

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred", ex);

        BaseResponse response = BaseResponse.error(
                "An unexpected error occurred. Please try again later.",
                "INTERNAL_ERROR"
        );

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}

