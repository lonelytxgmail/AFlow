package com.aflow.common.exception;

/**
 * Thrown when a flow execution encounters an unrecoverable error.
 */
public class FlowExecutionException extends AFlowException {

    private static final String ERROR_CODE = "FLOW_EXECUTION_ERROR";

    public FlowExecutionException(String message) {
        super(ERROR_CODE, message);
    }

    public FlowExecutionException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    public FlowExecutionException(String flowInstanceId, String nodeId, String message) {
        super(ERROR_CODE, "Flow '" + flowInstanceId + "' failed at node '" + nodeId + "': " + message);
    }

    public FlowExecutionException(String flowInstanceId, String nodeId, String message, Throwable cause) {
        super(ERROR_CODE, "Flow '" + flowInstanceId + "' failed at node '" + nodeId + "': " + message, cause);
    }
}
