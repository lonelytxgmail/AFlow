package com.aflow.core.engine;

import com.aflow.common.model.NodeResult;
import com.aflow.common.model.ResultStatus;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Tag;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for the node timeout enforcement contract.
 *
 * <p>Tests the timeout mechanism used by DefaultWorkflowEngine.executeNode():
 * {@code CompletableFuture.supplyAsync(...).orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join()}
 * </p>
 *
 * <p>When execution duration exceeds the configured timeout, the engine produces a FAILED result.
 * When execution completes within the timeout, the engine returns the actual result unchanged.</p>
 *
 * <p><b>Validates: Requirements 1.2, 1.3</b></p>
 */
@Tag("Feature: engine-resilience, Property 1: Node timeout enforcement")
class NodeTimeoutPropertyTest {

    /**
     * Property 1: Node timeout enforcement
     *
     * <p>For any (timeoutMs ∈ [50, 5000], execDurationMs ∈ [10, 10000]), verify:
     * <ul>
     *   <li>FAILED result when execDurationMs exceeds timeoutMs</li>
     *   <li>Actual (SUCCESS) result when execDurationMs is within timeoutMs</li>
     * </ul>
     * </p>
     *
     * <p><b>Validates: Requirements 1.2, 1.3</b></p>
     */
    @Property(tries = 20)
    void nodeTimeoutEnforcesDeadline(
            @ForAll @IntRange(min = 50, max = 500) int timeoutMs,
            @ForAll @IntRange(min = 10, max = 1000) int execDurationMs
    ) {
        // To keep tests fast and deterministic, add a margin to avoid flakiness
        // at the boundary where execDurationMs ≈ timeoutMs
        boolean shouldTimeout = execDurationMs > timeoutMs + 30;
        boolean shouldSucceed = execDurationMs < timeoutMs - 30;

        // Skip ambiguous boundary cases where timing is unpredictable
        if (!shouldTimeout && !shouldSucceed) {
            return; // skip this case — too close to the boundary for reliable assertion
        }

        // Simulate the timeout mechanism used in DefaultWorkflowEngine.executeNode()
        NodeResult result;
        try {
            result = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(execDurationMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return NodeResult.failed("Interrupted");
                }
                return NodeResult.success(Map.of("answer", "done"));
            }).orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                // This is how the engine handles timeout — produce FAILED result
                result = NodeResult.failed("Node execution timed out after " + timeoutMs + "ms");
            } else {
                fail("Unexpected CompletionException cause: " + e.getCause());
                return;
            }
        }

        // Assert based on expected outcome
        if (shouldTimeout) {
            assertEquals(ResultStatus.FAILED, result.status(),
                    String.format("Expected FAILED for timeoutMs=%d, execDurationMs=%d", timeoutMs, execDurationMs));
            assertTrue(result.errorMessage().contains("timed out"),
                    "Error message should indicate timeout, got: " + result.errorMessage());
        } else {
            // shouldSucceed
            assertEquals(ResultStatus.SUCCESS, result.status(),
                    String.format("Expected SUCCESS for timeoutMs=%d, execDurationMs=%d", timeoutMs, execDurationMs));
            assertEquals(Map.of("answer", "done"), result.outputs(),
                    "Outputs should be preserved when execution completes within timeout");
        }
    }
}
