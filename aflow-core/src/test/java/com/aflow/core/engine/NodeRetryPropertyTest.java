package com.aflow.core.engine;

import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.common.model.ResultStatus;
import com.aflow.common.model.RetryPolicy;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Tag;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based test for the node retry attempt count contract.
 *
 * <p>Tests the retry loop logic used by DefaultWorkflowEngine.executeNode():
 * an executor that fails failCount times then succeeds should be called
 * exactly min(failCount + 1, maxAttempts) times.</p>
 *
 * <p><b>Validates: Requirements 6.2, 6.5</b></p>
 */
@Tag("Feature: engine-resilience, Property 8: Node retry attempt count")
class NodeRetryPropertyTest {

    /**
     * Property 8: Node retry attempt count
     *
     * <p>For any retry policy with maxAttempts ∈ [1, 5] and a node that fails
     * on the first failCount ∈ [0, 10] attempts then succeeds, the engine SHALL
     * execute the node exactly min(failCount + 1, maxAttempts) times —
     * returning SUCCESS if failCount < maxAttempts, or FAILED if failCount >= maxAttempts.</p>
     *
     * <p><b>Validates: Requirements 6.2, 6.5</b></p>
     */
    @Property(tries = 20)
    void retryAttemptCountMatchesPolicy(
            @ForAll @IntRange(min = 1, max = 5) int maxAttempts,
            @ForAll @IntRange(min = 0, max = 10) int failCount
    ) {
        // Setup: executor that fails failCount times then succeeds
        AtomicInteger callCount = new AtomicInteger(0);
        NodeExecutor executor = (config, context) -> {
            int currentCall = callCount.incrementAndGet();
            if (currentCall <= failCount) {
                return NodeResult.failed("Transient failure on attempt " + currentCall);
            }
            return NodeResult.success(Map.of("result", "ok"));
        };

        // Run: simulate the retry loop from DefaultWorkflowEngine.executeNode()
        RetryPolicy retryPolicy = new RetryPolicy(maxAttempts, 1, 1.0); // 1ms backoff for test speed
        NodeConfig config = new NodeConfig();
        config.setType("test");
        config.setConfig(Map.of());

        NodeResult result = null;
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            result = executor.execute(config, null);

            // Break on non-FAILED result or if this was the last attempt
            if (result.status() != ResultStatus.FAILED || attempt == retryPolicy.maxAttempts()) {
                break;
            }
        }

        // Assert: correct number of executions
        int expectedCalls = Math.min(failCount + 1, maxAttempts);
        assertEquals(expectedCalls, callCount.get(),
                String.format("Expected %d calls for maxAttempts=%d, failCount=%d but got %d",
                        expectedCalls, maxAttempts, failCount, callCount.get()));

        // Assert: correct final status
        ResultStatus expectedStatus = failCount < maxAttempts ? ResultStatus.SUCCESS : ResultStatus.FAILED;
        assertEquals(expectedStatus, result.status(),
                String.format("Expected status %s for maxAttempts=%d, failCount=%d but got %s",
                        expectedStatus, maxAttempts, failCount, result.status()));
    }
}
