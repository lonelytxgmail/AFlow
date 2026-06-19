package com.aflow.common.model;

import java.util.Map;

/**
 * Value object defining an automatic retry policy for node execution.
 * <p>
 * Configures how many times a failed node should be retried and the
 * exponential backoff between attempts.
 *
 * @param maxAttempts       maximum number of execution attempts (1 means no retry)
 * @param backoffMs         initial backoff duration in milliseconds
 * @param backoffMultiplier exponential multiplier applied to backoff on each subsequent attempt
 */
public record RetryPolicy(int maxAttempts, long backoffMs, double backoffMultiplier) {

    /** A policy that performs no retries — the node is executed exactly once. */
    public static final RetryPolicy NO_RETRY = new RetryPolicy(1, 0, 1.0);

    /**
     * Computes the backoff duration before the given attempt.
     * <p>
     * Attempt numbering is 1-based: attempt 1 is the first retry,
     * so backoff = backoffMs * multiplier^(attempt - 1).
     *
     * @param attempt the 1-based attempt number
     * @return the backoff duration in milliseconds
     */
    public long computeBackoff(int attempt) {
        return (long) (backoffMs * Math.pow(backoffMultiplier, attempt - 1));
    }

    /**
     * Parses a RetryPolicy from a {@link NodeDefinition}'s config map.
     * <p>
     * Expects an optional {@code "retryPolicy"} key in the node config containing a map with:
     * <ul>
     *   <li>{@code maxAttempts} — integer, defaults to 1</li>
     *   <li>{@code backoffMs} — long, defaults to 1000</li>
     *   <li>{@code backoffMultiplier} — double, defaults to 2.0</li>
     * </ul>
     * If the {@code retryPolicy} key is absent or null, returns {@link #NO_RETRY}.
     *
     * @param nodeDef the node definition to parse from
     * @return the parsed RetryPolicy, or NO_RETRY if not configured
     */
    @SuppressWarnings("unchecked")
    public static RetryPolicy fromNodeDef(NodeDefinition nodeDef) {
        Object retryConfig = nodeDef.getConfig().get("retryPolicy");
        if (retryConfig == null) {
            return NO_RETRY;
        }

        Map<String, Object> configMap = (Map<String, Object>) retryConfig;

        int maxAttempts = configMap.containsKey("maxAttempts")
                ? ((Number) configMap.get("maxAttempts")).intValue()
                : 1;

        long backoffMs = configMap.containsKey("backoffMs")
                ? ((Number) configMap.get("backoffMs")).longValue()
                : 1000L;

        double backoffMultiplier = configMap.containsKey("backoffMultiplier")
                ? ((Number) configMap.get("backoffMultiplier")).doubleValue()
                : 2.0;

        return new RetryPolicy(maxAttempts, backoffMs, backoffMultiplier);
    }
}
