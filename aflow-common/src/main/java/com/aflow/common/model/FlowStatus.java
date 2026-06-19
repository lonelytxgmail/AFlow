package com.aflow.common.model;

/**
 * Lifecycle status of a flow instance.
 */
public enum FlowStatus {
    /** Flow has been created but not yet started. */
    PENDING,
    /** Flow is currently executing nodes. */
    RUNNING,
    /** Flow execution is paused (breakpoint, manual suspend, or awaiting external input). */
    SUSPENDED,
    /** Flow completed all nodes successfully. */
    COMPLETED,
    /** Flow terminated due to an error. */
    FAILED,
    /** Flow was explicitly cancelled by the user. */
    CANCELLED
}
