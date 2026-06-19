package com.aflow.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    @Test
    void mapper_isConfiguredCorrectly() {
        ObjectMapper mapper = JsonUtil.MAPPER;
        assertNotNull(mapper);
        // Should not fail on unknown properties
        assertFalse(mapper.getDeserializationConfig()
                .isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        // Should not write dates as timestamps
        assertFalse(mapper.getSerializationConfig()
                .isEnabled(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    @Test
    void toJson_simpleMap() {
        Map<String, Object> data = Map.of("name", "test", "value", 42);
        String json = JsonUtil.toJson(data);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"test\""));
        assertTrue(json.contains("42"));
    }

    @Test
    void fromJson_simpleMap() {
        String json = "{\"name\":\"test\",\"value\":42}";
        Map<String, Object> result = JsonUtil.fromJson(json, Map.class);
        assertEquals("test", result.get("name"));
        assertEquals(42, result.get("value"));
    }

    @Test
    void fromJson_ignoresUnknownProperties() {
        String json = "{\"known\":\"value\",\"unknown\":123}";
        Map<String, Object> result = JsonUtil.fromJson(json, Map.class);
        assertEquals("value", result.get("known"));
        // Should not throw on unknown property
    }

    @Test
    void toPrettyJson_formatsWithIndentation() {
        Map<String, Object> data = Map.of("key", "val");
        String pretty = JsonUtil.toPrettyJson(data);
        assertTrue(pretty.contains("\n"));
        assertTrue(pretty.contains("  ")); // indentation
    }

    @Test
    void deepClone_createsIndependentCopy() {
        Map<String, Object> original = new HashMap<>();
        original.put("a", 1);
        original.put("b", "two");

        @SuppressWarnings("unchecked")
        Map<String, Object> cloned = JsonUtil.deepClone(original, Map.class);

        assertEquals(original, cloned);
        // Verify it's a different object
        assertNotSame(original, cloned);
    }

    @Test
    void toMap_fromObject() {
        Map<String, Object> data = new HashMap<>();
        data.put("x", 10);
        data.put("y", "hello");

        Map<String, Object> result = JsonUtil.toMap(data);
        assertEquals(10, result.get("x"));
        assertEquals("hello", result.get("y"));
    }

    @Test
    void toMap_null_returnsEmptyMap() {
        Map<String, Object> result = JsonUtil.toMap(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void toJson_and_fromJson_roundTrip() {
        Map<String, Object> original = Map.of(
                "string", "hello",
                "number", 99,
                "bool", true
        );

        String json = JsonUtil.toJson(original);
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = JsonUtil.fromJson(json, Map.class);

        assertEquals(original.get("string"), deserialized.get("string"));
        assertEquals(original.get("number"), deserialized.get("number"));
        assertEquals(original.get("bool"), deserialized.get("bool"));
    }

    @Test
    void fromJson_invalidJson_throwsException() {
        assertThrows(RuntimeException.class, () ->
                JsonUtil.fromJson("not valid json{{{", Map.class));
    }

    @Test
    void toJson_instantSerializesAsIsoString() {
        Instant now = Instant.parse("2024-01-15T10:30:00Z");
        String json = JsonUtil.toJson(now);
        assertTrue(json.contains("2024-01-15"));
        assertTrue(json.contains("10:30:00"));
        // Should not be a numeric timestamp
        assertFalse(json.matches("^\\d+$"));
    }
}
