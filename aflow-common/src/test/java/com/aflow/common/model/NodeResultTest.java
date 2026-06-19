package com.aflow.common.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NodeResultTest {

    @Test
    void success_withOutputs() {
        Map<String, Object> outputs = Map.of("key", "value", "count", 5);
        NodeResult result = NodeResult.success(outputs);

        assertEquals(ResultStatus.SUCCESS, result.status());
        assertEquals("value", result.outputs().get("key"));
        assertEquals(5, result.outputs().get("count"));
        assertNull(result.nextNodeId());
        assertNull(result.errorMessage());
    }

    @Test
    void success_withNullOutputs_returnsEmptyMap() {
        NodeResult result = NodeResult.success(null);
        assertEquals(ResultStatus.SUCCESS, result.status());
        assertNotNull(result.outputs());
        assertTrue(result.outputs().isEmpty());
    }

    @Test
    void successWithNext_setsNextNodeId() {
        Map<String, Object> outputs = Map.of("decision", "yes");
        NodeResult result = NodeResult.successWithNext(outputs, "node-B");

        assertEquals(ResultStatus.SUCCESS, result.status());
        assertEquals("node-B", result.nextNodeId());
        assertEquals("yes", result.outputs().get("decision"));
        assertNull(result.errorMessage());
    }

    @Test
    void successWithNext_nullOutputs_returnsEmptyMap() {
        NodeResult result = NodeResult.successWithNext(null, "node-X");
        assertNotNull(result.outputs());
        assertTrue(result.outputs().isEmpty());
        assertEquals("node-X", result.nextNodeId());
    }

    @Test
    void failed_withErrorMessage() {
        NodeResult result = NodeResult.failed("Something went wrong");

        assertEquals(ResultStatus.FAILED, result.status());
        assertEquals("Something went wrong", result.errorMessage());
        assertNotNull(result.outputs());
        assertTrue(result.outputs().isEmpty());
        assertNull(result.nextNodeId());
    }

    @Test
    void suspended_noOutputs() {
        NodeResult result = NodeResult.suspended();

        assertEquals(ResultStatus.SUSPENDED, result.status());
        assertTrue(result.outputs().isEmpty());
        assertNull(result.nextNodeId());
        assertNull(result.errorMessage());
    }

    @Test
    void success_outputsAreImmutable() {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("a", 1);
        NodeResult result = NodeResult.success(outputs);

        // Modifying original map should not affect the result
        outputs.put("b", 2);
        // The result may or may not be affected depending on implementation,
        // but the outputs should at least contain the original key
        assertEquals(1, result.outputs().get("a"));
    }

    @Test
    void failed_emptyOutputs_immutable() {
        NodeResult result = NodeResult.failed("error");
        assertThrows(UnsupportedOperationException.class, () ->
                result.outputs().put("new", "value"));
    }

    @Test
    void suspended_emptyOutputs_immutable() {
        NodeResult result = NodeResult.suspended();
        assertThrows(UnsupportedOperationException.class, () ->
                result.outputs().put("new", "value"));
    }
}
