package com.aflow.common.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete definition of a workflow: its nodes, edges, and variable schema.
 * <p>
 * This is the "blueprint" — a flow definition can be instantiated multiple times
 * as flow instances. Definitions are versioned to support safe evolution.
 */
public class FlowDefinition {

    private String id;
    private String name;
    private int version;

    /** Input variable definitions with types, required flags, and defaults. */
    private Map<String, VariableDefinition> variables = new HashMap<>();

    /** Flow-scoped environment values exposed to expressions as read-only runtime config. */
    private Map<String, Object> environment = new HashMap<>();

    /** Ordered list of nodes in this flow. */
    private List<NodeDefinition> nodes = new ArrayList<>();

    /** Edges connecting nodes — defines execution order and conditional branches. */
    private List<EdgeDefinition> edges = new ArrayList<>();

    /** Strategy for handling partial failures during parallel wave execution. Defaults to FAIL_FAST. */
    private ParallelStrategy parallelStrategy = ParallelStrategy.FAIL_FAST;

    public FlowDefinition() {
    }

    // ─── Getters / Setters ──────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Map<String, VariableDefinition> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, VariableDefinition> variables) {
        this.variables = variables != null ? variables : new HashMap<>();
    }

    public Map<String, Object> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, Object> environment) {
        this.environment = environment != null ? environment : new HashMap<>();
    }

    public List<NodeDefinition> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeDefinition> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
    }

    public List<EdgeDefinition> getEdges() {
        return edges;
    }

    public void setEdges(List<EdgeDefinition> edges) {
        this.edges = edges != null ? edges : new ArrayList<>();
    }

    public ParallelStrategy getParallelStrategy() {
        return parallelStrategy;
    }

    public void setParallelStrategy(ParallelStrategy parallelStrategy) {
        this.parallelStrategy = parallelStrategy != null ? parallelStrategy : ParallelStrategy.FAIL_FAST;
    }

    /**
     * Find a node definition by ID, or {@code null} if not found.
     */
    public NodeDefinition findNode(String nodeId) {
        return nodes.stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return "FlowDefinition{id='" + id + "', name='" + name + "', version=" + version +
                ", nodes=" + nodes.size() + ", edges=" + edges.size() + '}';
    }
}
