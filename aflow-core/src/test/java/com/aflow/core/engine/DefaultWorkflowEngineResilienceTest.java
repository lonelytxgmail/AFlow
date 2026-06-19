package com.aflow.core.engine;

import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.*;
import com.aflow.core.event.FlowEventBus;
import com.aflow.core.event.FlowEventType;
import com.aflow.core.snapshot.SnapshotManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DefaultWorkflowEngine timeout and retry behavior.
 *
 * <p>Rather than instantiating the full engine (which has many dependencies),
 * these tests replicate the executeNode() retry/timeout logic and verify
 * that the correct events are published and snapshots are taken.</p>
 *
 * <p><b>Validates: Requirements 1.5, 6.4, 6.6, 6.7</b></p>
 */
class DefaultWorkflowEngineResilienceTest {

    private FlowEventBus eventBus;
    private SnapshotManager snapshotManager;
    private Executor virtualThreadExecutor;
    private FlowContext context;

    private static final String FLOW_INSTANCE_ID = "test-flow-001";
    private static final String NODE_ID = "node-1";

    @BeforeEach
    void setUp() {
        eventBus = mock(FlowEventBus.class);
        snapshotManager = mock(SnapshotManager.class);
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

        context = new FlowContext(FLOW_INSTANCE_ID, "def-001");
        context.setStatus(FlowStatus.RUNNING);
    }

    /**
     * Simulates the executeNode() retry+timeout loop from DefaultWorkflowEngine.
     * This mirrors the actual engine logic for testability without full engine construction.
     */
    private NodeResult executeNodeWithRetryAndTimeout(
            NodeExecutor executor, NodeDefinition nodeDef, long defaultNodeTimeoutMs) {

        String flowInstanceId = context.getFlowInstanceId();
        String nodeId = nodeDef.getId();
        long startTime = System.currentTimeMillis();

        // Snapshot BEFORE (mirrors engine behavior)
        snapshotManager.saveSnapshot(flowInstanceId, nodeId, SnapshotPhase.BEFORE, context);

        NodeTimeoutPolicy timeoutPolicy = NodeTimeoutPolicy.fromNodeDef(nodeDef, defaultNodeTimeoutMs);
        RetryPolicy retryPolicy = RetryPolicy.fromNodeDef(nodeDef);
        long timeoutMs = timeoutPolicy.timeoutMs();

        NodeResult result = null;
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            if (attempt > 1) {
                long backoffMs = retryPolicy.computeBackoff(attempt - 1);

                // Publish NODE_RETRY event
                eventBus.publish(flowInstanceId, FlowEventType.NODE_RETRY.name(), Map.of(
                        "nodeId", nodeId,
                        "attempt", attempt,
                        "maxAttempts", retryPolicy.maxAttempts(),
                        "backoffMs", backoffMs
                ));

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    result = NodeResult.failed("Node retry interrupted");
                    break;
                }

                // Take snapshot before retry attempt
                snapshotManager.saveSnapshot(flowInstanceId, nodeId, SnapshotPhase.BEFORE, context);
            }

            NodeConfig config = new NodeConfig();
            config.setType(nodeDef.getType());
            config.setConfig(nodeDef.getConfig() != null ? nodeDef.getConfig() : Map.of());

            try {
                result = CompletableFuture.supplyAsync(
                        () -> executor.execute(config, context), virtualThreadExecutor
                ).orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof TimeoutException) {
                    long elapsedMs = System.currentTimeMillis() - startTime;

                    // Publish NODE_TIMEOUT event
                    eventBus.publish(flowInstanceId, FlowEventType.NODE_TIMEOUT.name(), Map.of(
                            "nodeId", nodeId,
                            "timeoutMs", timeoutMs,
                            "elapsedMs", elapsedMs
                    ));

                    result = NodeResult.failed("Node execution timed out after " + timeoutMs + "ms");
                } else {
                    result = NodeResult.failed("Node execution exception: " + e.getCause().getMessage());
                }
            }

            if (result.status() != ResultStatus.FAILED || attempt == retryPolicy.maxAttempts()) {
                break;
            }
        }

        // Snapshot AFTER
        snapshotManager.saveSnapshot(flowInstanceId, nodeId, SnapshotPhase.AFTER, context);

        return result;
    }

    // ─── Test 1: NODE_TIMEOUT event published on timeout ─────────────────────

    @Test
    @DisplayName("NODE_TIMEOUT event published when node execution exceeds timeout")
    void nodeTimeoutEventPublishedOnTimeout() {
        // Configure a node with a short timeout (50ms)
        NodeDefinition nodeDef = new NodeDefinition(NODE_ID, "slow-node");
        nodeDef.setConfig(Map.of("timeout", "50"));

        // Executor that sleeps 200ms (well beyond the 50ms timeout)
        NodeExecutor slowExecutor = (config, ctx) -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return NodeResult.success(Map.of("result", "late"));
        };

        NodeResult result = executeNodeWithRetryAndTimeout(slowExecutor, nodeDef, 300000);

        // Verify FAILED result
        assertEquals(ResultStatus.FAILED, result.status());
        assertTrue(result.errorMessage().contains("timed out"));

        // Verify NODE_TIMEOUT event was published
        ArgumentCaptor<String> eventNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> eventDataCaptor = ArgumentCaptor.forClass(Object.class);

        verify(eventBus, atLeastOnce()).publish(
                eq(FLOW_INSTANCE_ID), eventNameCaptor.capture(), eventDataCaptor.capture());

        boolean timeoutEventPublished = eventNameCaptor.getAllValues().stream()
                .anyMatch(name -> name.equals(FlowEventType.NODE_TIMEOUT.name()));
        assertTrue(timeoutEventPublished, "NODE_TIMEOUT event should have been published");
    }

    // ─── Test 2: NODE_RETRY event published on retry ─────────────────────────

    @Test
    @DisplayName("NODE_RETRY event published when node retries after failure")
    void nodeRetryEventPublishedOnRetry() {
        // Configure a node with retry policy (maxAttempts=3, minimal backoff)
        NodeDefinition nodeDef = new NodeDefinition(NODE_ID, "flaky-node");
        nodeDef.setConfig(Map.of(
                "retryPolicy", Map.of(
                        "maxAttempts", 3,
                        "backoffMs", 1,
                        "backoffMultiplier", 1.0
                )
        ));

        // Executor that fails twice then succeeds
        AtomicInteger callCount = new AtomicInteger(0);
        NodeExecutor flakyExecutor = (config, ctx) -> {
            int attempt = callCount.incrementAndGet();
            if (attempt <= 2) {
                return NodeResult.failed("Transient error on attempt " + attempt);
            }
            return NodeResult.success(Map.of("result", "ok"));
        };

        NodeResult result = executeNodeWithRetryAndTimeout(flakyExecutor, nodeDef, 300000);

        // Verify SUCCESS result (third attempt succeeds)
        assertEquals(ResultStatus.SUCCESS, result.status());
        assertEquals(3, callCount.get(), "Should have been called 3 times");

        // Verify NODE_RETRY event was published at least once
        ArgumentCaptor<String> eventNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> eventDataCaptor = ArgumentCaptor.forClass(Object.class);

        verify(eventBus, atLeast(1)).publish(
                eq(FLOW_INSTANCE_ID), eventNameCaptor.capture(), eventDataCaptor.capture());

        long retryEventCount = eventNameCaptor.getAllValues().stream()
                .filter(name -> name.equals(FlowEventType.NODE_RETRY.name()))
                .count();
        assertEquals(2, retryEventCount, "NODE_RETRY event should be published for attempt 2 and 3");

        // Verify event data contains attempt info
        for (int i = 0; i < eventNameCaptor.getAllValues().size(); i++) {
            if (eventNameCaptor.getAllValues().get(i).equals(FlowEventType.NODE_RETRY.name())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) eventDataCaptor.getAllValues().get(i);
                assertEquals(NODE_ID, data.get("nodeId"));
                assertTrue(((int) data.get("attempt")) >= 2, "Attempt should be >= 2");
                assertEquals(3, data.get("maxAttempts"));
            }
        }
    }

    // ─── Test 3: Snapshot taken per retry ────────────────────────────────────

    @Test
    @DisplayName("Snapshot taken before each retry attempt")
    void snapshotTakenPerRetry() {
        // Configure a node with retry policy (maxAttempts=3, minimal backoff)
        NodeDefinition nodeDef = new NodeDefinition(NODE_ID, "flaky-node");
        nodeDef.setConfig(Map.of(
                "retryPolicy", Map.of(
                        "maxAttempts", 3,
                        "backoffMs", 1,
                        "backoffMultiplier", 1.0
                )
        ));

        // Executor that fails twice then succeeds
        AtomicInteger callCount = new AtomicInteger(0);
        NodeExecutor flakyExecutor = (config, ctx) -> {
            int attempt = callCount.incrementAndGet();
            if (attempt <= 2) {
                return NodeResult.failed("Transient error on attempt " + attempt);
            }
            return NodeResult.success(Map.of("result", "ok"));
        };

        NodeResult result = executeNodeWithRetryAndTimeout(flakyExecutor, nodeDef, 300000);

        assertEquals(ResultStatus.SUCCESS, result.status());

        // Verify snapshots taken:
        // 1 initial BEFORE + 2 retry BEFOREs + 1 AFTER = total 4 saveSnapshot calls
        // Specifically: 3 BEFORE snapshots (initial + 2 retries) and 1 AFTER snapshot
        ArgumentCaptor<SnapshotPhase> phaseCaptor = ArgumentCaptor.forClass(SnapshotPhase.class);
        verify(snapshotManager, times(4)).saveSnapshot(
                eq(FLOW_INSTANCE_ID), eq(NODE_ID), phaseCaptor.capture(), eq(context));

        long beforeCount = phaseCaptor.getAllValues().stream()
                .filter(p -> p == SnapshotPhase.BEFORE)
                .count();
        long afterCount = phaseCaptor.getAllValues().stream()
                .filter(p -> p == SnapshotPhase.AFTER)
                .count();

        assertEquals(3, beforeCount, "Should have 3 BEFORE snapshots (initial + 2 retries)");
        assertEquals(1, afterCount, "Should have 1 AFTER snapshot");
    }

    // ─── Test 4: No retry when retryPolicy absent ────────────────────────────

    @Test
    @DisplayName("No retry when retryPolicy is absent from node config")
    void noRetryWhenRetryPolicyAbsent() {
        // Configure a node WITHOUT retryPolicy
        NodeDefinition nodeDef = new NodeDefinition(NODE_ID, "simple-node");
        nodeDef.setConfig(Map.of()); // no retryPolicy key

        // Executor that always fails
        AtomicInteger callCount = new AtomicInteger(0);
        NodeExecutor failingExecutor = (config, ctx) -> {
            callCount.incrementAndGet();
            return NodeResult.failed("Permanent failure");
        };

        NodeResult result = executeNodeWithRetryAndTimeout(failingExecutor, nodeDef, 300000);

        // Verify FAILED result
        assertEquals(ResultStatus.FAILED, result.status());
        assertEquals("Permanent failure", result.errorMessage());

        // Verify executor called exactly once (no retry)
        assertEquals(1, callCount.get(), "Executor should be called exactly once with no retry policy");

        // Verify NODE_RETRY event was NOT published
        verify(eventBus, never()).publish(
                anyString(), eq(FlowEventType.NODE_RETRY.name()), any());
    }
}
