package com.aflow.common.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    // ─── fromNodeDef: full config map ────────────────────────────────

    @Test
    void fromNodeDef_withFullRetryPolicyConfig_parsesAllFields() {
        NodeDefinition nodeDef = new NodeDefinition("node-1", "http");
        Map<String, Object> retryConfig = Map.of(
                "maxAttempts", 5,
                "backoffMs", 2000L,
                "backoffMultiplier", 3.0
        );
        nodeDef.setConfig(Map.of("retryPolicy", retryConfig));

        RetryPolicy policy = RetryPolicy.fromNodeDef(nodeDef);

        assertEquals(5, policy.maxAttempts());
        assertEquals(2000L, policy.backoffMs());
        assertEquals(3.0, policy.backoffMultiplier());
    }

    // ─── fromNodeDef: missing fields use defaults ────────────────────

    @Test
    void fromNodeDef_withEmptyRetryPolicyMap_usesDefaults() {
        NodeDefinition nodeDef = new NodeDefinition("node-2", "script");
        Map<String, Object> retryConfig = new HashMap<>();
        nodeDef.setConfig(Map.of("retryPolicy", retryConfig));

        RetryPolicy policy = RetryPolicy.fromNodeDef(nodeDef);

        assertEquals(1, policy.maxAttempts());
        assertEquals(1000L, policy.backoffMs());
        assertEquals(2.0, policy.backoffMultiplier());
    }

    @Test
    void fromNodeDef_withPartialConfig_missingFieldsUseDefaults() {
        NodeDefinition nodeDef = new NodeDefinition("node-3", "http");
        Map<String, Object> retryConfig = new HashMap<>();
        retryConfig.put("maxAttempts", 3);
        // backoffMs and backoffMultiplier are absent
        nodeDef.setConfig(Map.of("retryPolicy", retryConfig));

        RetryPolicy policy = RetryPolicy.fromNodeDef(nodeDef);

        assertEquals(3, policy.maxAttempts());
        assertEquals(1000L, policy.backoffMs());
        assertEquals(2.0, policy.backoffMultiplier());
    }

    // ─── fromNodeDef: absent retryPolicy → NO_RETRY ─────────────────

    @Test
    void fromNodeDef_withNoRetryPolicyKey_returnsNoRetry() {
        NodeDefinition nodeDef = new NodeDefinition("node-4", "condition");
        nodeDef.setConfig(Map.of("someOtherKey", "value"));

        RetryPolicy policy = RetryPolicy.fromNodeDef(nodeDef);

        assertSame(RetryPolicy.NO_RETRY, policy);
    }

    @Test
    void fromNodeDef_withNullRetryPolicyValue_returnsNoRetry() {
        NodeDefinition nodeDef = new NodeDefinition("node-5", "http");
        Map<String, Object> config = new HashMap<>();
        config.put("retryPolicy", null);
        nodeDef.setConfig(config);

        RetryPolicy policy = RetryPolicy.fromNodeDef(nodeDef);

        assertSame(RetryPolicy.NO_RETRY, policy);
    }

    @Test
    void fromNodeDef_withEmptyConfig_returnsNoRetry() {
        NodeDefinition nodeDef = new NodeDefinition("node-6", "http");
        nodeDef.setConfig(new HashMap<>());

        RetryPolicy policy = RetryPolicy.fromNodeDef(nodeDef);

        assertSame(RetryPolicy.NO_RETRY, policy);
    }

    // ─── NO_RETRY constant ──────────────────────────────────────────

    @Test
    void noRetryConstant_hasExpectedValues() {
        assertEquals(1, RetryPolicy.NO_RETRY.maxAttempts());
        assertEquals(0L, RetryPolicy.NO_RETRY.backoffMs());
        assertEquals(1.0, RetryPolicy.NO_RETRY.backoffMultiplier());
    }

    // ─── computeBackoff correctness ─────────────────────────────────

    @Test
    void computeBackoff_attempt1_returnsBackoffMs() {
        RetryPolicy policy = new RetryPolicy(3, 1000, 2.0);

        // backoffMs * 2.0^(1-1) = 1000 * 1.0 = 1000
        assertEquals(1000L, policy.computeBackoff(1));
    }

    @Test
    void computeBackoff_attempt2_appliesMultiplierOnce() {
        RetryPolicy policy = new RetryPolicy(3, 1000, 2.0);

        // backoffMs * 2.0^(2-1) = 1000 * 2.0 = 2000
        assertEquals(2000L, policy.computeBackoff(2));
    }

    @Test
    void computeBackoff_attempt3_appliesMultiplierTwice() {
        RetryPolicy policy = new RetryPolicy(3, 1000, 2.0);

        // backoffMs * 2.0^(3-1) = 1000 * 4.0 = 4000
        assertEquals(4000L, policy.computeBackoff(3));
    }

    @Test
    void computeBackoff_withNonIntegerMultiplier() {
        RetryPolicy policy = new RetryPolicy(5, 500, 1.5);

        // attempt 1: 500 * 1.5^0 = 500
        assertEquals(500L, policy.computeBackoff(1));
        // attempt 2: 500 * 1.5^1 = 750
        assertEquals(750L, policy.computeBackoff(2));
        // attempt 3: 500 * 1.5^2 = 500 * 2.25 = 1125
        assertEquals(1125L, policy.computeBackoff(3));
    }

    @Test
    void computeBackoff_noRetryPolicy_returnsZero() {
        // NO_RETRY has backoffMs=0, so all attempts return 0
        assertEquals(0L, RetryPolicy.NO_RETRY.computeBackoff(1));
        assertEquals(0L, RetryPolicy.NO_RETRY.computeBackoff(5));
    }

    @Test
    void computeBackoff_higherAttempts_growsExponentially() {
        RetryPolicy policy = new RetryPolicy(10, 100, 3.0);

        // attempt 4: 100 * 3.0^3 = 100 * 27 = 2700
        assertEquals(2700L, policy.computeBackoff(4));
        // attempt 5: 100 * 3.0^4 = 100 * 81 = 8100
        assertEquals(8100L, policy.computeBackoff(5));
    }
}
