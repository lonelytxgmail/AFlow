package com.aflow.common.model;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable result of a single node execution.
 *
 * @param status       the execution result status
 * @param outputs      key-value outputs produced by the node
 * @param nextNodeId   explicit next node ID for conditional routing (nullable)
 * @param errorMessage error description when status is FAILED (nullable)
 */
public record NodeResult(
        ResultStatus status,
        Map<String, Object> outputs,
        String nextNodeId,
        String errorMessage
) {

    /**
     * Create a successful result with outputs.
     */
    public static NodeResult success(Map<String, Object> outputs) {
        return new NodeResult(ResultStatus.SUCCESS, outputs != null ? outputs : Collections.emptyMap(), null, null);
    }

    /**
     * Create a successful result with outputs and an explicit next node.
     */
    public static NodeResult successWithNext(Map<String, Object> outputs, String nextNodeId) {
        return new NodeResult(ResultStatus.SUCCESS, outputs != null ? outputs : Collections.emptyMap(), nextNodeId, null);
    }

    /**
     * Create a failed result with an error message.
     */
    public static NodeResult failed(String errorMessage) {
        return new NodeResult(ResultStatus.FAILED, Collections.emptyMap(), null, errorMessage);
    }

    /**
     * Create a suspended result (awaiting external input or manual resume).
     */
    public static NodeResult suspended() {
        return new NodeResult(ResultStatus.SUSPENDED, Collections.emptyMap(), null, null);
    }
}
