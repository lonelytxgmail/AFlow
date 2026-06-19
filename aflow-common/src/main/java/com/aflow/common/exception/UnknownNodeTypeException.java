package com.aflow.common.exception;

/**
 * Thrown when a node type is referenced that has no registered executor.
 */
public class UnknownNodeTypeException extends AFlowException {

    private static final String ERROR_CODE = "UNKNOWN_NODE_TYPE";

    public UnknownNodeTypeException(String nodeType) {
        super(ERROR_CODE, "Unknown node type: '" + nodeType + "'. No executor is registered for this type.");
    }

    public UnknownNodeTypeException(String nodeType, Throwable cause) {
        super(ERROR_CODE, "Unknown node type: '" + nodeType + "'. No executor is registered for this type.", cause);
    }
}
