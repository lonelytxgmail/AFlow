package com.aflow.common.model;

/**
 * Value object representing the timeout policy for a single node execution.
 * <p>
 * The timeout value is resolved from the node's config map (key: "timeout"),
 * falling back to an engine-level default when not explicitly configured.
 */
public record NodeTimeoutPolicy(long timeoutMs) {

    /**
     * Resolves a {@link NodeTimeoutPolicy} from the given node definition.
     * <p>
     * If the node's config contains a "timeout" entry, its value is parsed as
     * the timeout in milliseconds. Otherwise, the provided engine default is used.
     *
     * @param nodeDef       the node definition to read config from
     * @param engineDefault the fallback timeout in milliseconds from engine configuration
     * @return a resolved {@link NodeTimeoutPolicy}
     */
    public static NodeTimeoutPolicy fromNodeDef(NodeDefinition nodeDef, long engineDefault) {
        Object cfgTimeout = nodeDef.getConfig().get("timeout");
        long ms = cfgTimeout != null ? Long.parseLong(cfgTimeout.toString()) : engineDefault;
        return new NodeTimeoutPolicy(ms);
    }
}
