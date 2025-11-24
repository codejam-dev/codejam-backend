package com.codejam.commons.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse {

    private boolean success;
    private String message;
    private Object data;
    private String errorCode;
    private LocalDateTime timestamp;

    public static BaseResponse success(Object data) {
        return BaseResponse.builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static BaseResponse success(String message, Object data) {
        return BaseResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static BaseResponse success(String message) {
        return BaseResponse.builder()
                .success(true)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static BaseResponse error(String message) {
        return BaseResponse.builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static BaseResponse error(String message, String errorCode) {
        return BaseResponse.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static BaseResponse error(String message, String errorCode, Object data) {
        return BaseResponse.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

