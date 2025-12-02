package com.codejam.commons.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorType {
    USER_EXISTS("User already exists", HttpStatus.CONFLICT),
    USER_NOT_FOUND("User not found", HttpStatus.NOT_FOUND),
    INVALID_TOKEN("Token is invalid or expired", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("Invalid username or password", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("Access denied", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR("Validation failed", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    PROFILE_NOT_FOUND("Profile not found", HttpStatus.NOT_FOUND),
    PROFILE_EXISTS("Profile already exists", HttpStatus.CONFLICT);

    private final String message;
    private final HttpStatus status;

    ErrorType(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }

}

