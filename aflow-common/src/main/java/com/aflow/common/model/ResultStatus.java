package com.aflow.common.model;

/**
 * Result status of a single node execution.
 */
public enum ResultStatus {
    /** Node executed successfully. */
    SUCCESS,
    /** Node execution failed with an error. */
    FAILED,
    /** Node execution is suspended (awaiting external input or manual resume). */
    SUSPENDED
}
