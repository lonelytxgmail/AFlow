package com.aflow.common.model;

/**
 * Definition of a directed edge between two nodes.
 * <p>
 * Edges may carry a SpEL condition expression. During execution, conditional
 * edges are evaluated against the {@link FlowContext} to determine which
 * outgoing path to take (e.g., from a condition/switch node).
 * <p>
 * Edges have a {@code type} that determines when they are evaluated:
 * <ul>
 *   <li>{@code normal} (default) — taken when the source node succeeds</li>
 *   <li>{@code error} — taken when the source node fails, enabling try-catch semantics</li>
 * </ul>
 */
public class EdgeDefinition {

    /** Edge type constants. */
    public static final String TYPE_NORMAL = "normal";
    public static final String TYPE_ERROR = "error";

    /** Source node ID. */
    private String from;

    /** Target node ID. */
    private String to;

    /** Optional SpEL expression — if present, this edge is only taken when the expression evaluates to true. */
    private String condition;

    /**
     * Edge type: "normal" (default) or "error".
     * Normal edges are followed on successful node execution.
     * Error edges are followed when a node fails, enabling try-catch flow patterns.
     */
    private String type;

    public EdgeDefinition() {
    }

    public EdgeDefinition(String from, String to) {
        this.from = from;
        this.to = to;
    }

    public EdgeDefinition(String from, String to, String condition) {
        this.from = from;
        this.to = to;
        this.condition = condition;
    }

    public EdgeDefinition(String from, String to, String condition, String type) {
        this.from = from;
        this.to = to;
        this.condition = condition;
        this.type = type;
    }

    // ─── Helper methods ─────────────────────────────────────────────

    /**
     * Returns the effective edge type, defaulting to "normal" if not set.
     */
    public String getEffectiveType() {
        return type != null && !type.isBlank() ? type : TYPE_NORMAL;
    }

    /**
     * Check if this is an error edge (used for try-catch routing).
     */
    public boolean isErrorEdge() {
        return TYPE_ERROR.equalsIgnoreCase(getEffectiveType());
    }

    /**
     * Check if this is a normal edge (default execution path).
     */
    public boolean isNormalEdge() {
        return !isErrorEdge();
    }

    // ─── Getters / Setters ──────────────────────────────────────────

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "EdgeDefinition{" + from + " -> " + to +
                (type != null ? " type=" + type : "") +
                (condition != null ? " [" + condition + "]" : "") + '}';
    }
}
