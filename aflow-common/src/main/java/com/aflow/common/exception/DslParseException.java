package com.aflow.common.exception;

/**
 * Thrown when flow definition JSON cannot be parsed or is structurally invalid.
 */
public class DslParseException extends AFlowException {

    private static final String ERROR_CODE = "DSL_PARSE_ERROR";

    public DslParseException(String message) {
        super(ERROR_CODE, message);
    }

    public DslParseException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
