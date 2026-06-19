package com.aflow.common.exception;

/**
 * Base runtime exception for all AFlow errors.
 * <p>
 * Carries an error code for programmatic error handling and
 * a human-readable message for logging and API responses.
 */
public class AFlowException extends RuntimeException {

    private final String errorCode;

    public AFlowException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AFlowException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "AFlowException{errorCode='" + errorCode + "', message='" + getMessage() + "'}";
    }
}
