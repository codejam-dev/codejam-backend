package com.codejam.execution.exception;

public class ExecutionTimeoutException extends RuntimeException {
    public ExecutionTimeoutException(String message) {
        super(message);
    }
}
