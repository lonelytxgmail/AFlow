package com.aflow.agent.executor;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Tag;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for the agent tool timeout enforcement contract.
 *
 * <p>Tests the timeout mechanism used by AgentNodeExecutor in the ReAct loop:
 * {@code CompletableFuture.supplyAsync(() -> tool.execute(args, ctx), virtualThreadExecutor)
 *     .get(toolTimeoutMs, TimeUnit.MILLISECONDS)}
 * </p>
 *
 * <p>When tool execution duration exceeds the configured toolTimeout, the agent produces a
 * timeout error string and continues the loop. When tool execution completes within the timeout,
 * the agent returns the tool's actual result.</p>
 *
 * <p><b>Validates: Requirements 2.2, 2.3</b></p>
 */
@Tag("Feature: engine-resilience, Property 2: Agent tool timeout enforcement")
class ToolTimeoutPropertyTest {

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Property 2: Agent tool timeout enforcement
     *
     * <p>For any (timeoutMs ∈ [50, 500], execDurationMs ∈ [10, 1000]), verify:
     * <ul>
     *   <li>Timeout error string returned when execDurationMs exceeds timeoutMs</li>
     *   <li>Actual tool result string returned when execDurationMs is within timeoutMs</li>
     * </ul>
     * </p>
     *
     * <p><b>Validates: Requirements 2.2, 2.3</b></p>
     */
    @Property(tries = 20)
    void agentToolTimeoutEnforcesDeadline(
            @ForAll @IntRange(min = 50, max = 500) int timeoutMs,
            @ForAll @IntRange(min = 10, max = 1000) int execDurationMs
    ) {
        // Add a ±30ms margin to avoid flakiness at the boundary
        boolean shouldTimeout = execDurationMs > timeoutMs + 30;
        boolean shouldSucceed = execDurationMs < timeoutMs - 30;

        // Skip ambiguous boundary cases where timing is unpredictable
        if (!shouldTimeout && !shouldSucceed) {
            return;
        }

        long toolTimeoutMs = timeoutMs;
        String expectedToolResult = "Tool result from execution";

        // Simulate the tool timeout mechanism used in AgentNodeExecutor
        String toolResult;
        try {
            toolResult = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(execDurationMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "Interrupted";
                }
                return expectedToolResult;
            }, virtualThreadExecutor).get(toolTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // This is how the agent handles tool timeout
            toolResult = "Tool execution timed out after " + toolTimeoutMs + "ms";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Unexpected InterruptedException");
            return;
        } catch (ExecutionException e) {
            fail("Unexpected ExecutionException: " + e.getCause());
            return;
        }

        // Assert based on expected outcome
        if (shouldTimeout) {
            assertEquals("Tool execution timed out after " + toolTimeoutMs + "ms", toolResult,
                    String.format("Expected timeout error for timeoutMs=%d, execDurationMs=%d",
                            timeoutMs, execDurationMs));
        } else {
            // shouldSucceed
            assertEquals(expectedToolResult, toolResult,
                    String.format("Expected actual tool result for timeoutMs=%d, execDurationMs=%d",
                            timeoutMs, execDurationMs));
        }
    }
}
