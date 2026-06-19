package com.aflow.core.event;

/**
 * Types of events emitted during flow execution.
 */
public enum FlowEventType {
    /** Flow execution has started. */
    FLOW_STARTED,
    /** Flow execution completed successfully. */
    FLOW_COMPLETED,
    /** Flow execution failed with an error. */
    FLOW_FAILED,
    /** Flow execution was cancelled by the user. */
    FLOW_CANCELLED,
    /** Engine is about to execute a node. */
    NODE_ENTER,
    /** Node execution completed (successfully or with error). */
    NODE_EXIT,
    /** Node execution failed with an error. */
    NODE_ERROR,
    /** Node execution resulted in a suspend (awaiting input). */
    NODE_SUSPENDED,
    /** Node execution is being retried. */
    NODE_RETRY,
    /** Node execution exceeded its configured timeout. */
    NODE_TIMEOUT,
    /** Agent is reasoning about the next action. */
    AGENT_THINK,
    /** Agent is invoking a tool. */
    AGENT_ACT,
    /** Agent observed a tool result. */
    AGENT_OBSERVE,
    /** Agent tool call exceeded its configured timeout. */
    AGENT_TOOL_TIMEOUT,
    /** Agent pruned messages due to token budget constraints. */
    AGENT_TOKEN_PRUNE,
    /** Agent completed with a final answer. */
    AGENT_DONE,
    /** Agent tool call was rate-limited (not executed). */
    AGENT_TOOL_RATE_LIMITED,
    /** Agent output failed schema validation. */
    AGENT_OUTPUT_VALIDATION_FAILED,
    /** Parallel wave execution started (fork point). */
    PARALLEL_FORK,
    /** Parallel wave execution completed (join point). */
    PARALLEL_JOIN
}
