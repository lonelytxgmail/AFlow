package com.aflow.core.engine;

import com.aflow.common.model.*;
import com.aflow.core.event.EventPersistenceService;
import com.aflow.core.event.FlowEventBus;
import com.aflow.core.event.FlowEventType;
import com.aflow.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Parallel wave executor for DAG-based workflow execution.
 * <p>
 * Decomposes the DAG into topological "waves" where nodes in the same wave
 * have no mutual dependencies and can be executed in parallel.
 * <p>
 * Wave decomposition algorithm:
 * <ul>
 *   <li>wave[0] = nodes with no incoming edges (source nodes)</li>
 *   <li>wave[k] = nodes whose ALL predecessors are in wave[0..k-1]</li>
 * </ul>
 * <p>
 * Each node in a parallel wave executes with its own independent timeout
 * ({@link NodeTimeoutPolicy}) and retry ({@link RetryPolicy}) policies.
 */
public class ParallelWaveExecutor {

    private static final Logger log = LoggerFactory.getLogger(ParallelWaveExecutor.class);

    private final Executor virtualThreadExecutor;
    private final FlowEventBus eventBus;
    private final EventPersistenceService eventPersistenceService;

    public ParallelWaveExecutor(Executor virtualThreadExecutor,
                                 FlowEventBus eventBus,
                                 EventPersistenceService eventPersistenceService) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.eventBus = eventBus;
        this.eventPersistenceService = eventPersistenceService;
    }

    /**
     * Compute topological wave decomposition of a DAG.
     * <p>
     * Nodes are grouped into waves such that all dependencies of nodes in wave[k]
     * are satisfied by nodes in waves [0..k-1]. Nodes within the same wave have
     * no mutual dependencies and can be executed in parallel.
     * <p>
     * Only considers normal edges (not error edges) for dependency computation.
     *
     * @param definition the flow definition containing nodes and edges
     * @return ordered list of waves, each wave is a set of node IDs
     */
    public List<Set<String>> computeWaves(FlowDefinition definition) {
        List<NodeDefinition> nodes = definition.getNodes();
        List<EdgeDefinition> edges = definition.getEdges() != null ? definition.getEdges() : List.of();

        // Build adjacency: inDegree count and predecessors for each node (normal edges only)
        Map<String, Set<String>> predecessors = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (NodeDefinition node : nodes) {
            predecessors.put(node.getId(), new HashSet<>());
            inDegree.put(node.getId(), 0);
        }

        for (EdgeDefinition edge : edges) {
            if (edge.isNormalEdge()) {
                String from = edge.getFrom();
                String to = edge.getTo();
                if (predecessors.containsKey(to) && predecessors.containsKey(from)) {
                    predecessors.get(to).add(from);
                    inDegree.merge(to, 1, Integer::sum);
                }
            }
        }

        List<Set<String>> waves = new ArrayList<>();
        Set<String> remaining = new HashSet<>(inDegree.keySet());

        while (!remaining.isEmpty()) {
            // Current wave: all nodes with inDegree == 0 among remaining nodes
            Set<String> currentWave = remaining.stream()
                    .filter(nodeId -> inDegree.get(nodeId) == 0)
                    .collect(Collectors.toSet());

            if (currentWave.isEmpty()) {
                // Cycle detected — include all remaining nodes in the last wave to avoid infinite loop
                log.warn("Cycle detected in DAG, remaining nodes: {}", remaining);
                waves.add(new LinkedHashSet<>(remaining));
                break;
            }

            waves.add(currentWave);
            remaining.removeAll(currentWave);

            // Reduce inDegree for successors of nodes in the current wave
            for (String nodeId : currentWave) {
                for (EdgeDefinition edge : edges) {
                    if (edge.isNormalEdge() && edge.getFrom().equals(nodeId) && remaining.contains(edge.getTo())) {
                        inDegree.merge(edge.getTo(), -1, Integer::sum);
                    }
                }
            }
        }

        return waves;
    }

    /**
     * Result of executing a parallel wave.
     */
    public record WaveExecutionResult(
            /** Map of nodeId to its execution result */
            Map<String, NodeResult> results,
            /** Whether the wave execution succeeded overall */
            boolean success,
            /** Error message if wave failed */
            String errorMessage
    ) {
        public static WaveExecutionResult success(Map<String, NodeResult> results) {
            return new WaveExecutionResult(results, true, null);
        }

        public static WaveExecutionResult failed(Map<String, NodeResult> results, String errorMessage) {
            return new WaveExecutionResult(results, false, errorMessage);
        }
    }

    /**
     * Execute a wave of nodes in parallel according to the configured strategy.
     * <p>
     * Each node executes independently with its own timeout and retry policy.
     * The parallel strategy determines how partial failures are handled.
     *
     * @param wave              set of node IDs to execute in parallel
     * @param context           flow context (thread-safe for concurrent access)
     * @param definition        flow definition for looking up node configs
     * @param parallelStrategy  failure handling strategy
     * @param nodeExecutionFunc function to execute a single node (nodeId → NodeResult)
     * @return wave execution result containing individual node results
     */
    public WaveExecutionResult executeWaveParallel(
            Set<String> wave,
            FlowContext context,
            FlowDefinition definition,
            ParallelStrategy parallelStrategy,
            java.util.function.BiFunction<FlowContext, NodeDefinition, NodeResult> nodeExecutionFunc) {

        String flowInstanceId = context.getFlowInstanceId();

        // Publish PARALLEL_FORK event
        eventPersistenceService.saveEvent(flowInstanceId, null, FlowEventType.PARALLEL_FORK,
                JsonUtil.toJson(Map.of("wave", wave, "strategy", parallelStrategy.name())), 0);
        eventBus.publish(flowInstanceId, FlowEventType.PARALLEL_FORK.name(), Map.of(
                "wave", wave,
                "strategy", parallelStrategy.name()
        ));

        log.info("Parallel wave fork: flow={}, nodes={}, strategy={}", flowInstanceId, wave, parallelStrategy);

        // Launch each node as a CompletableFuture
        Map<String, CompletableFuture<NodeResult>> futureMap = new LinkedHashMap<>();
        for (String nodeId : wave) {
            NodeDefinition nodeDef = definition.findNode(nodeId);
            if (nodeDef == null) {
                futureMap.put(nodeId, CompletableFuture.completedFuture(
                        NodeResult.failed("Node not found in definition: " + nodeId)));
                continue;
            }

            CompletableFuture<NodeResult> future = CompletableFuture.supplyAsync(
                    () -> nodeExecutionFunc.apply(context, nodeDef),
                    virtualThreadExecutor
            );
            futureMap.put(nodeId, future);
        }

        // Wait for results according to strategy
        Map<String, NodeResult> results = new ConcurrentHashMap<>();

        switch (parallelStrategy) {
            case FAIL_FAST -> executeFailFast(futureMap, results, flowInstanceId);
            case WAIT_ALL -> executeWaitAll(futureMap, results);
            case BEST_EFFORT -> executeWaitAll(futureMap, results);
        }

        // Publish PARALLEL_JOIN event
        Map<String, String> resultSummary = results.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().status().name()));
        eventPersistenceService.saveEvent(flowInstanceId, null, FlowEventType.PARALLEL_JOIN,
                JsonUtil.toJson(Map.of("results", resultSummary, "strategy", parallelStrategy.name())), 0);
        eventBus.publish(flowInstanceId, FlowEventType.PARALLEL_JOIN.name(), Map.of(
                "results", resultSummary,
                "strategy", parallelStrategy.name()
        ));

        log.info("Parallel wave join: flow={}, results={}", flowInstanceId, resultSummary);

        // Determine overall success based on strategy
        boolean hasFailure = results.values().stream()
                .anyMatch(r -> r.status() == ResultStatus.FAILED);

        if (hasFailure && parallelStrategy != ParallelStrategy.BEST_EFFORT) {
            String failedNodes = results.entrySet().stream()
                    .filter(e -> e.getValue().status() == ResultStatus.FAILED)
                    .map(e -> e.getKey() + ": " + e.getValue().errorMessage())
                    .collect(Collectors.joining("; "));
            return WaveExecutionResult.failed(results, "Parallel wave failed: " + failedNodes);
        }

        return WaveExecutionResult.success(results);
    }

    /**
     * Fail-fast: complete as soon as any future fails by canceling the rest.
     */
    private void executeFailFast(Map<String, CompletableFuture<NodeResult>> futureMap,
                                  Map<String, NodeResult> results,
                                  String flowInstanceId) {
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futureMap.values().toArray(new CompletableFuture[0]));

        // Attach a handler to each future to detect first failure
        CompletableFuture<Void> failFastFuture = new CompletableFuture<>();

        for (Map.Entry<String, CompletableFuture<NodeResult>> entry : futureMap.entrySet()) {
            String nodeId = entry.getKey();
            entry.getValue().whenComplete((result, throwable) -> {
                if (throwable != null) {
                    results.put(nodeId, NodeResult.failed("Execution exception: " + throwable.getMessage()));
                    failFastFuture.completeExceptionally(throwable);
                } else {
                    results.put(nodeId, result);
                    if (result.status() == ResultStatus.FAILED) {
                        failFastFuture.completeExceptionally(
                                new RuntimeException("Node " + nodeId + " failed: " + result.errorMessage()));
                    }
                }
            });
        }

        try {
            // Wait for either allOf to complete or failFast to trigger
            CompletableFuture.anyOf(allOf, failFastFuture).join();
        } catch (CompletionException e) {
            log.debug("Fail-fast triggered for flow={}: {}", flowInstanceId, e.getMessage());
            // Cancel remaining futures
            for (CompletableFuture<NodeResult> f : futureMap.values()) {
                f.cancel(true);
            }
        }

        // Collect any remaining results (some might have completed despite cancel)
        for (Map.Entry<String, CompletableFuture<NodeResult>> entry : futureMap.entrySet()) {
            if (!results.containsKey(entry.getKey())) {
                try {
                    NodeResult result = entry.getValue().getNow(NodeResult.failed("Cancelled by fail-fast"));
                    results.put(entry.getKey(), result);
                } catch (Exception e) {
                    results.put(entry.getKey(), NodeResult.failed("Cancelled by fail-fast"));
                }
            }
        }
    }

    /**
     * Wait-all / Best-effort: wait for every future to settle (success or failure).
     */
    private void executeWaitAll(Map<String, CompletableFuture<NodeResult>> futureMap,
                                 Map<String, NodeResult> results) {
        for (Map.Entry<String, CompletableFuture<NodeResult>> entry : futureMap.entrySet()) {
            String nodeId = entry.getKey();
            try {
                NodeResult result = entry.getValue().join();
                results.put(nodeId, result);
            } catch (CompletionException e) {
                results.put(nodeId, NodeResult.failed("Execution exception: " +
                        (e.getCause() != null ? e.getCause().getMessage() : e.getMessage())));
            } catch (CancellationException e) {
                results.put(nodeId, NodeResult.failed("Execution cancelled"));
            }
        }
    }
}
