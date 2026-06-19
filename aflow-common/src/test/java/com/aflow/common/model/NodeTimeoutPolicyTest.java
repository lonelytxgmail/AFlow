package com.aflow.common.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NodeTimeoutPolicyTest {

    @Test
    void fromNodeDef_explicitTimeoutOverridesDefault() {
        NodeDefinition nodeDef = new NodeDefinition("node-1", "http");
        nodeDef.setConfig(Map.of("timeout", 5000));

        NodeTimeoutPolicy policy = NodeTimeoutPolicy.fromNodeDef(nodeDef, 300000L);

        assertEquals(5000L, policy.timeoutMs());
    }

    @Test
    void fromNodeDef_fallsBackToEngineDefault_whenTimeoutAbsent() {
        NodeDefinition nodeDef = new NodeDefinition("node-1", "http");
        nodeDef.setConfig(new HashMap<>());

        NodeTimeoutPolicy policy = NodeTimeoutPolicy.fromNodeDef(nodeDef, 300000L);

        assertEquals(300000L, policy.timeoutMs());
    }

    @Test
    void fromNodeDef_parsesStringTimeout() {
        NodeDefinition nodeDef = new NodeDefinition("node-1", "script");
        nodeDef.setConfig(Map.of("timeout", "10000"));

        NodeTimeoutPolicy policy = NodeTimeoutPolicy.fromNodeDef(nodeDef, 300000L);

        assertEquals(10000L, policy.timeoutMs());
    }

    @Test
    void fromNodeDef_differentEngineDefaults_produceDifferentFallbacks() {
        NodeDefinition nodeDef = new NodeDefinition("node-1", "http");
        nodeDef.setConfig(new HashMap<>());

        NodeTimeoutPolicy policy1 = NodeTimeoutPolicy.fromNodeDef(nodeDef, 60000L);
        NodeTimeoutPolicy policy2 = NodeTimeoutPolicy.fromNodeDef(nodeDef, 120000L);

        assertEquals(60000L, policy1.timeoutMs());
        assertEquals(120000L, policy2.timeoutMs());
        assertNotEquals(policy1.timeoutMs(), policy2.timeoutMs());
    }
}
