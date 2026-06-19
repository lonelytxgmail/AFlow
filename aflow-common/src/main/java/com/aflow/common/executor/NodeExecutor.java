package com.aflow.common.executor;

import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;

/**
 * Functional interface for node execution.
 * <p>
 * Each node type (HTTP, condition, script, transform, etc.) implements this
 * interface to define its execution behavior. Implementations are discovered
 * via the {@link com.aflow.common.annotation.FlowNode} annotation and
 * registered in the {@code NodeRegistry}.
 */
@FunctionalInterface
public interface NodeExecutor {

    /**
     * Execute this node with the given configuration and flow context.
     *
     * @param config  the node's type-specific configuration
     * @param context the current flow execution context (mutable)
     * @return the result of this node's execution
     */
    NodeResult execute(NodeConfig config, FlowContext context);
}
