package com.aflow.core.engine;

import com.aflow.common.model.FlowContext;

import java.util.Map;
import java.util.Set;

/**
 * The workflow engine interface — the heart of AFlow.
 * <p>
 * Manages the full lifecycle of flow instances: start, resume, retry, suspend,
 * cancel, and single-step debug execution.
 */
public interface WorkflowEngine {

    /**
     * Start a new flow instance from a published definition.
     */
    FlowContext start(String flowDefinitionId, Map<String, Object> inputs);

    /**
     * Resume a suspended flow instance, optionally injecting additional inputs.
     */
    FlowContext resume(String flowInstanceId, Map<String, Object> additionalInputs);

    /**
     * Retry a failed flow instance from the specified node.
     */
    FlowContext retry(String flowInstanceId, String fromNodeId);

    /**
     * Suspend a running flow instance.
     */
    FlowContext suspend(String flowInstanceId);

    /**
     * Cancel a flow instance.
     */
    FlowContext cancel(String flowInstanceId);

    /**
     * Execute a single step (one node) and then suspend.
     */
    FlowContext step(String flowInstanceId);

    /**
     * Add a dynamic breakpoint on a node for a flow instance.
     */
    void addBreakpoint(String flowInstanceId, String nodeId);

    /**
     * Remove a dynamic breakpoint.
     */
    void removeBreakpoint(String flowInstanceId, String nodeId);

    /**
     * Get the current breakpoints for a flow instance.
     */
    Set<String> getBreakpoints(String flowInstanceId);

    /**
     * Update the context variables of a suspended flow instance (hot debug).
     */
    FlowContext updateContext(String flowInstanceId, Map<String, Object> variables);

    /**
     * Invalidate the cached definition for a flow definition ID.
     * Call this after updating a definition's DSL to ensure the engine uses the latest version.
     */
    void invalidateDefinitionCache(String definitionId);
}
