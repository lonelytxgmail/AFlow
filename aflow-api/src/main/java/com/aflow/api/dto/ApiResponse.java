package com.aflow.api.dto;

import java.time.Instant;

public record ApiResponse<T>(boolean success, String message, String errorCode, T data, Instant timestamp) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "success", null, data, Instant.now());
    }
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null, Instant.now());
    }
    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(false, message, errorCode, null, Instant.now());
    }
}
