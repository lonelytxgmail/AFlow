package com.aflow.common.model;

/**
 * Schema definition for a flow input variable.
 * <p>
 * Used to declare the expected inputs of a flow definition,
 * including type, whether the variable is required, and its default value.
 */
public class VariableDefinition {

    /** Variable type: "string", "number", "boolean", "object", "array". */
    private String type;

    /** Whether this variable must be provided when starting the flow. */
    private boolean required;

    /** Default value used when the variable is not supplied. */
    private Object defaultValue;

    public VariableDefinition() {
    }

    public VariableDefinition(String type, boolean required, Object defaultValue) {
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
    }

    // ─── Getters / Setters ──────────────────────────────────────────

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public String toString() {
        return "VariableDefinition{type='" + type + "', required=" + required +
                ", defaultValue=" + defaultValue + '}';
    }
}
