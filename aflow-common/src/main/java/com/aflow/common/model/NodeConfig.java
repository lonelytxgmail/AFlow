package com.aflow.common.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime configuration for a node execution.
 * <p>
 * Wraps the node type, its type-specific configuration map,
 * and the output variable name where the result should be stored.
 */
public class NodeConfig {

    /** Node type identifier (e.g., "http", "condition", "script"). */
    private String type;

    /** Type-specific configuration parameters. */
    private Map<String, Object> config = new HashMap<>();

    /** Variable name in {@link FlowContext#getVariables()} to store the node result. */
    private String outputVariable;

    public NodeConfig() {
    }

    public NodeConfig(String type, Map<String, Object> config, String outputVariable) {
        this.type = type;
        this.config = config != null ? config : new HashMap<>();
        this.outputVariable = outputVariable;
    }

    // ─── Getters / Setters ──────────────────────────────────────────

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config != null ? config : new HashMap<>();
    }

    public String getOutputVariable() {
        return outputVariable;
    }

    public void setOutputVariable(String outputVariable) {
        this.outputVariable = outputVariable;
    }

    /**
     * Convenience accessor for a single config property with type coercion.
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String key) {
        return (T) config.get(key);
    }

    /**
     * Convenience accessor with a default value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String key, T defaultValue) {
        Object value = config.get(key);
        return value != null ? (T) value : defaultValue;
    }

    @Override
    public String toString() {
        return "NodeConfig{type='" + type + "', outputVariable='" + outputVariable + "', config=" + config + '}';
    }
}
