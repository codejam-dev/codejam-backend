package com.codejam.commons.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CustomException extends RuntimeException {
    private final String errorType;
    private final String customMessage;
    private final HttpStatus httpStatus;

    public CustomException(String errorType, String customMessage, HttpStatus httpStatus) {
        super(customMessage);
        this.errorType = errorType;
        this.customMessage = customMessage;
        this.httpStatus = httpStatus;
    }
}

