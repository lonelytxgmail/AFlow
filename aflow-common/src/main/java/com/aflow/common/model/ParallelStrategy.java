package com.aflow.common.model;

/**
 * Strategy for handling partial failures during parallel wave execution.
 * <p>
 * Configured at the {@link FlowDefinition} level to control how the engine
 * responds when some nodes in a parallel wave fail while others succeed.
 */
public enum ParallelStrategy {

    /**
     * Fail-fast: if any node in the wave fails, cancel remaining running nodes
     * and immediately propagate the failure.
     */
    FAIL_FAST,

    /**
     * Wait-all: wait for every node in the wave to complete (success or failure),
     * then fail the wave if any node failed.
     */
    WAIT_ALL,

    /**
     * Best-effort: wait for every node in the wave to complete, ignore failures,
     * and continue with whatever results are available.
     */
    BEST_EFFORT
}
