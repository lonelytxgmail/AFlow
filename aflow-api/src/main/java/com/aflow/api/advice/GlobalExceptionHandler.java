package com.aflow.api.advice;

import com.aflow.api.dto.ApiResponse;
import com.aflow.common.exception.AFlowException;
import com.aflow.common.exception.DslParseException;
import com.aflow.common.exception.FlowExecutionException;
import com.aflow.common.exception.FlowNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FlowNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<?> handleNotFound(FlowNotFoundException e) {
        log.warn("Not found: {}", e.getMessage());
        return ApiResponse.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(DslParseException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<?> handleDslParse(DslParseException e) {
        log.warn("DSL parse error: {}", e.getMessage());
        return ApiResponse.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(FlowExecutionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<?> handleFlowExecution(FlowExecutionException e) {
        log.warn("Flow execution error: {}", e.getMessage());
        return ApiResponse.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(AFlowException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleAFlowException(AFlowException e) {
        log.warn("AFlow exception: {}", e.getMessage());
        return ApiResponse.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleBadRequest(HttpMessageNotReadableException e) {
        log.warn("Bad request body: {}", e.getMessage());
        return ApiResponse.error("BAD_REQUEST", "Invalid JSON request body");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Validation error: {}", message);
        return ApiResponse.error("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<?> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMessage());
        return ApiResponse.error("METHOD_NOT_ALLOWED", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return ApiResponse.error("BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleGeneralException(Exception e) {
        log.error("Internal server error", e);
        return ApiResponse.error("INTERNAL_ERROR", "Internal error: " + e.getMessage());
    }
}
