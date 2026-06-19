package com.aflow.common.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable execution context for a running flow instance.
 * <p>
 * Carries all runtime state: variables, metadata, execution path,
 * breakpoints, and debug flags. Passed to each {@link com.aflow.common.executor.NodeExecutor}
 * during execution.
 * <p>
 * Thread safety: The variables map uses {@link ConcurrentHashMap} and the execution path
 * uses a synchronized list to support safe concurrent access during parallel wave execution.
 */
public class FlowContext {

    private String flowInstanceId;
    private String flowDefinitionId;

    /** Mutable workspace for flow variables — nodes read from and write to this map.
     *  Uses ConcurrentHashMap for thread-safe parallel access during wave execution. */
    private Map<String, Object> variables = new ConcurrentHashMap<>();

    /** Flow-level metadata (e.g., creator, tenant, tags). */
    private Map<String, Object> metadata = new ConcurrentHashMap<>();

    /** Environment variables: read-only global config loaded from FlowDefinition. Accessible via #env.xxx in SpEL. */
    private Map<String, Object> environment = new ConcurrentHashMap<>();

    private volatile FlowStatus status = FlowStatus.PENDING;
    private volatile String currentNodeId;

    /** Ordered list of node IDs that have been executed. Thread-safe for parallel writes. */
    private List<String> executionPath = Collections.synchronizedList(new ArrayList<>());

    /** Dynamic breakpoints set at runtime (node IDs). */
    private Set<String> breakpoints = ConcurrentHashMap.newKeySet();

    /** When true, the engine pauses after every node execution. */
    private boolean debugMode;

    // ─── Constructors ───────────────────────────────────────────────

    public FlowContext() {
    }

    public FlowContext(String flowInstanceId, String flowDefinitionId) {
        this.flowInstanceId = flowInstanceId;
        this.flowDefinitionId = flowDefinitionId;
    }

    // ─── Helper methods ─────────────────────────────────────────────

    /**
     * Put a single variable into the workspace.
     * Null values are stored as a sentinel to maintain ConcurrentHashMap compatibility.
     */
    public void putVariable(String key, Object value) {
        if (value == null) {
            this.variables.remove(key);
        } else {
            this.variables.put(key, value);
        }
    }

    /**
     * Get a variable by key, or {@code null} if absent.
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) this.variables.get(key);
    }

    /**
     * Merge a map of outputs into the variables workspace.
     * Null values in the source map will remove the corresponding key.
     */
    public void mergeOutputs(Map<String, Object> outputs) {
        if (outputs != null) {
            for (Map.Entry<String, Object> entry : outputs.entrySet()) {
                if (entry.getValue() == null) {
                    this.variables.remove(entry.getKey());
                } else {
                    this.variables.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * Record a node as executed and set it as the current node.
     */
    public void recordNodeExecution(String nodeId) {
        this.executionPath.add(nodeId);
        this.currentNodeId = nodeId;
    }

    /**
     * Check whether a breakpoint is set on the given node.
     */
    public boolean hasBreakpoint(String nodeId) {
        return this.breakpoints.contains(nodeId);
    }

    // ─── Getters / Setters ──────────────────────────────────────────

    public String getFlowInstanceId() {
        return flowInstanceId;
    }

    public void setFlowInstanceId(String flowInstanceId) {
        this.flowInstanceId = flowInstanceId;
    }

    public String getFlowDefinitionId() {
        return flowDefinitionId;
    }

    public void setFlowDefinitionId(String flowDefinitionId) {
        this.flowDefinitionId = flowDefinitionId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables != null ? new ConcurrentHashMap<>(variables) : new ConcurrentHashMap<>();
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new ConcurrentHashMap<>(metadata) : new ConcurrentHashMap<>();
    }

    public Map<String, Object> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, Object> environment) {
        this.environment = environment != null ? new ConcurrentHashMap<>(environment) : new ConcurrentHashMap<>();
    }

    public FlowStatus getStatus() {
        return status;
    }

    public void setStatus(FlowStatus status) {
        this.status = status;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public List<String> getExecutionPath() {
        return executionPath;
    }

    public void setExecutionPath(List<String> executionPath) {
        this.executionPath = executionPath != null ? Collections.synchronizedList(new ArrayList<>(executionPath)) : Collections.synchronizedList(new ArrayList<>());
    }

    public Set<String> getBreakpoints() {
        return breakpoints;
    }

    public void setBreakpoints(Set<String> breakpoints) {
        this.breakpoints = breakpoints != null ? ConcurrentHashMap.newKeySet() : ConcurrentHashMap.newKeySet();
        if (breakpoints != null) {
            this.breakpoints.addAll(breakpoints);
        }
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    @Override
    public String toString() {
        return "FlowContext{" +
                "flowInstanceId='" + flowInstanceId + '\'' +
                ", flowDefinitionId='" + flowDefinitionId + '\'' +
                ", status=" + status +
                ", currentNodeId='" + currentNodeId + '\'' +
                ", executionPath=" + executionPath +
                ", debugMode=" + debugMode +
                '}';
    }
}
