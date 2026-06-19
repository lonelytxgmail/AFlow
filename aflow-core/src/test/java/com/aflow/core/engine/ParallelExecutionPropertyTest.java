package com.aflow.core.engine;

import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.*;
import com.aflow.core.event.EventPersistenceService;
import com.aflow.core.event.FlowEventBus;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Tag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test: verifies that parallel (wave-based) DAG execution
 * produces the same final output as serial execution for any DAG topology.
 * <p>
 * The invariant: for a DAG of pure deterministic nodes, the set of outputs
 * (per node) must be identical regardless of whether nodes are executed
 * sequentially or in parallel waves.
 * <p>
 * <b>Validates: Requirements 8.1</b>
 */
@Tag("Feature: frontend-refactor, Property: parallel execution consistency")
class ParallelExecutionPropertyTest {

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Property: For any DAG of deterministic nodes, parallel wave execution
     * produces the same merged outputs as sequential topological execution.
     *
     * <p><b>Validates: Requirements 8.1</b></p>
     */
    @Property(tries = 50)
    void parallelExecutionMatchesSequentialExecution(
            @ForAll("dagDefinitions") FlowDefinition definition
    ) {
        // Create a deterministic node executor: each node outputs its own ID and a computed value
        NodeExecutor deterministicExecutor = (config, context) -> {
            String nodeId = config.getConfig().getOrDefault("nodeId", "unknown").toString();
            Map<String, Object> outputs = new HashMap<>();
            outputs.put(nodeId + "_result", "computed_" + nodeId);
            return NodeResult.success(outputs);
        };

        // --- Sequential execution ---
        Map<String, Object> sequentialOutputs = executeSequentially(definition, deterministicExecutor);

        // --- Parallel (wave-based) execution ---
        Map<String, Object> parallelOutputs = executeWithWaves(definition, deterministicExecutor);

        // --- Verify consistency ---
        assertEquals(sequentialOutputs, parallelOutputs,
                String.format("Outputs differ between sequential and parallel execution for DAG with %d nodes. " +
                                "Sequential=%s, Parallel=%s",
                        definition.getNodes().size(), sequentialOutputs, parallelOutputs));
    }

    /**
     * Property: Wave computation produces a valid topological ordering
     * (every node appears exactly once, and no node appears before its predecessors).
     *
     * <p><b>Validates: Requirements 8.1</b></p>
     */
    @Property(tries = 50)
    void waveDecompositionIsValidTopologicalOrder(
            @ForAll("dagDefinitions") FlowDefinition definition
    ) {
        FlowEventBus eventBus = new FlowEventBus();
        EventPersistenceService eps = createNoOpEventPersistence();
        ParallelWaveExecutor waveExecutor = new ParallelWaveExecutor(executor, eventBus, eps);

        List<Set<String>> waves = waveExecutor.computeWaves(definition);

        // All nodes should appear exactly once across all waves
        Set<String> allNodeIds = definition.getNodes().stream()
                .map(NodeDefinition::getId)
                .collect(Collectors.toSet());
        Set<String> nodesInWaves = waves.stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        assertEquals(allNodeIds, nodesInWaves,
                "All nodes must appear in waves exactly once");

        // Total count should match (no duplicates)
        long totalCount = waves.stream().mapToLong(Set::size).sum();
        assertEquals(allNodeIds.size(), totalCount,
                "Total nodes in waves should equal total nodes in definition");

        // Dependency ordering: for each node in wave[k], all its predecessors must be in wave[0..k-1]
        Map<String, Integer> nodeToWave = new HashMap<>();
        for (int i = 0; i < waves.size(); i++) {
            for (String nodeId : waves.get(i)) {
                nodeToWave.put(nodeId, i);
            }
        }

        List<EdgeDefinition> normalEdges = (definition.getEdges() != null ? definition.getEdges() : List.<EdgeDefinition>of())
                .stream()
                .filter(EdgeDefinition::isNormalEdge)
                .toList();

        for (EdgeDefinition edge : normalEdges) {
            int fromWave = nodeToWave.getOrDefault(edge.getFrom(), -1);
            int toWave = nodeToWave.getOrDefault(edge.getTo(), -1);
            if (fromWave >= 0 && toWave >= 0) {
                assertTrue(fromWave < toWave,
                        String.format("Edge %s->%s: source wave %d should be < target wave %d",
                                edge.getFrom(), edge.getTo(), fromWave, toWave));
            }
        }
    }

    // ─── Sequential execution helper ─────────────────────────────────

    private Map<String, Object> executeSequentially(FlowDefinition definition, NodeExecutor executor) {
        FlowEventBus eventBus = new FlowEventBus();
        EventPersistenceService eps = createNoOpEventPersistence();
        ParallelWaveExecutor waveExecutor = new ParallelWaveExecutor(this.executor, eventBus, eps);

        List<Set<String>> waves = waveExecutor.computeWaves(definition);
        Map<String, Object> outputs = new HashMap<>();

        // Execute each wave sequentially, nodes within a wave also sequentially
        for (Set<String> wave : waves) {
            for (String nodeId : wave) {
                NodeDefinition nodeDef = definition.findNode(nodeId);
                if (nodeDef == null) continue;

                NodeConfig config = new NodeConfig();
                config.setType(nodeDef.getType());
                Map<String, Object> configMap = new HashMap<>(nodeDef.getConfig());
                configMap.put("nodeId", nodeId);
                config.setConfig(configMap);

                NodeResult result = executor.execute(config, null);
                if (result.status() == ResultStatus.SUCCESS && result.outputs() != null) {
                    outputs.putAll(result.outputs());
                }
            }
        }

        return outputs;
    }

    // ─── Parallel wave execution helper ──────────────────────────────

    private Map<String, Object> executeWithWaves(FlowDefinition definition, NodeExecutor executor) {
        FlowEventBus eventBus = new FlowEventBus();
        EventPersistenceService eps = createNoOpEventPersistence();
        ParallelWaveExecutor waveExecutor = new ParallelWaveExecutor(this.executor, eventBus, eps);

        List<Set<String>> waves = waveExecutor.computeWaves(definition);
        FlowContext context = new FlowContext("test-instance", "test-def");
        context.setStatus(FlowStatus.RUNNING);
        Map<String, Object> outputs = new ConcurrentHashMap<>();

        for (Set<String> wave : waves) {
            if (wave.size() == 1) {
                // Single node — execute directly
                String nodeId = wave.iterator().next();
                NodeDefinition nodeDef = definition.findNode(nodeId);
                if (nodeDef == null) continue;

                NodeConfig config = new NodeConfig();
                config.setType(nodeDef.getType());
                Map<String, Object> configMap = new HashMap<>(nodeDef.getConfig());
                configMap.put("nodeId", nodeId);
                config.setConfig(configMap);

                NodeResult result = executor.execute(config, null);
                if (result.status() == ResultStatus.SUCCESS && result.outputs() != null) {
                    outputs.putAll(result.outputs());
                }
            } else {
                // Parallel wave execution
                ParallelWaveExecutor.WaveExecutionResult waveResult = waveExecutor.executeWaveParallel(
                        wave, context, definition, ParallelStrategy.WAIT_ALL,
                        (ctx, nodeDef) -> {
                            NodeConfig config = new NodeConfig();
                            config.setType(nodeDef.getType());
                            Map<String, Object> configMap = new HashMap<>(nodeDef.getConfig());
                            configMap.put("nodeId", nodeDef.getId());
                            config.setConfig(configMap);
                            return executor.execute(config, null);
                        }
                );

                for (NodeResult result : waveResult.results().values()) {
                    if (result.status() == ResultStatus.SUCCESS && result.outputs() != null) {
                        outputs.putAll(result.outputs());
                    }
                }
            }
        }

        return outputs;
    }

    // ─── Arbitrary providers ─────────────────────────────────────────

    /**
     * Generates random DAG definitions with 2-8 nodes and valid acyclic edges.
     */
    @Provide
    Arbitrary<FlowDefinition> dagDefinitions() {
        return Arbitraries.integers().between(2, 8).flatMap(nodeCount -> {
            // Generate node IDs
            List<String> nodeIds = new ArrayList<>();
            for (int i = 0; i < nodeCount; i++) {
                nodeIds.add("node_" + i);
            }

            // Generate edges: only from lower-indexed nodes to higher-indexed nodes (ensures DAG)
            return Arbitraries.integers().between(1, Math.max(1, nodeCount * 2)).flatMap(edgeCount ->
                    Arbitraries.just(buildDag(nodeIds, edgeCount))
            );
        });
    }

    private FlowDefinition buildDag(List<String> nodeIds, int maxEdges) {
        FlowDefinition def = new FlowDefinition();
        def.setId("test-def");
        def.setName("Test DAG");
        def.setParallelStrategy(ParallelStrategy.WAIT_ALL);

        List<NodeDefinition> nodes = new ArrayList<>();
        for (String nodeId : nodeIds) {
            NodeDefinition nodeDef = new NodeDefinition(nodeId, "script");
            nodeDef.setName(nodeId);
            nodeDef.setConfig(Map.of("nodeId", nodeId));
            nodeDef.setOutput(nodeId + "_out");
            nodes.add(nodeDef);
        }
        def.setNodes(nodes);

        // Generate acyclic edges: from lower index to higher index only
        List<EdgeDefinition> edges = new ArrayList<>();
        Random rng = new Random(nodeIds.hashCode());
        int edgesAdded = 0;
        for (int i = 0; i < nodeIds.size() - 1 && edgesAdded < maxEdges; i++) {
            for (int j = i + 1; j < nodeIds.size() && edgesAdded < maxEdges; j++) {
                if (rng.nextDouble() < 0.4) {
                    edges.add(new EdgeDefinition(nodeIds.get(i), nodeIds.get(j)));
                    edgesAdded++;
                }
            }
        }

        // Ensure at least one edge if there are 2+ nodes
        if (edges.isEmpty() && nodeIds.size() >= 2) {
            edges.add(new EdgeDefinition(nodeIds.get(0), nodeIds.get(1)));
        }

        def.setEdges(edges);
        return def;
    }

    // ─── Test helpers ────────────────────────────────────────────────

    private EventPersistenceService createNoOpEventPersistence() {
        return new EventPersistenceService() {
            @Override
            public void saveEvent(String flowInstanceId, String nodeId,
                                   com.aflow.core.event.FlowEventType eventType, String data, long duration) {
                // No-op for tests
            }

            @Override
            public List<Map<String, Object>> findByFlowInstanceId(String flowInstanceId) {
                return List.of();
            }
        };
    }
}
