package com.aflow.common.exception;

/**
 * Thrown when a flow definition or flow instance cannot be found.
 */
public class FlowNotFoundException extends AFlowException {

    private static final String ERROR_CODE = "FLOW_NOT_FOUND";

    public FlowNotFoundException(String message) {
        super(ERROR_CODE, message);
    }

    public FlowNotFoundException(String entityType, String id) {
        super(ERROR_CODE, entityType + " not found with id: '" + id + "'");
    }
}
