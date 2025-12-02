package com.codejam.commons.constant;

public class ApiConstants {
    
    // API Paths
    public static final String API_V1 = "/api/v1";
    public static final String AUTH_PATH = "/api/auth";
    public static final String PROFILE_PATH = "/api/profile";
    
    // Headers
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    
    // Error Codes
    public static final String ERROR_CODE_VALIDATION = "VALIDATION_ERROR";
    public static final String ERROR_CODE_NOT_FOUND = "NOT_FOUND";
    public static final String ERROR_CODE_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_CODE_FORBIDDEN = "FORBIDDEN";
    public static final String ERROR_CODE_INTERNAL_SERVER = "INTERNAL_SERVER_ERROR";
    
    // Messages
    public static final String SUCCESS_MESSAGE = "Operation completed successfully";
    public static final String VALIDATION_ERROR_MESSAGE = "Validation failed";
    public static final String NOT_FOUND_MESSAGE = "Resource not found";
    public static final String UNAUTHORIZED_MESSAGE = "Unauthorized access";
}

