package com.aflow.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * Centralised JSON utility using a pre-configured {@link ObjectMapper}.
 * <p>
 * All AFlow components should use this utility rather than creating ad-hoc
 * ObjectMapper instances to ensure consistent serialization behaviour.
 */
public final class JsonUtil {

    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);

    /** Shared, thread-safe ObjectMapper instance. */
    public static final ObjectMapper MAPPER = createMapper();

    private JsonUtil() {
        // utility class — no instantiation
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    /**
     * Deep-clone an object by serializing to JSON and deserializing back.
     *
     * @param obj  the source object
     * @param type the target type
     * @param <T>  target type parameter
     * @return a deep copy of the source object
     */
    public static <T> T deepClone(Object obj, Class<T> type) {
        try {
            String json = MAPPER.writeValueAsString(obj);
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("Failed to deep-clone object of type {}: {}", type.getSimpleName(), e.getMessage());
            throw new RuntimeException("JSON deep-clone failed", e);
        }
    }

    /**
     * Serialize an object to a JSON string.
     *
     * @param obj the object to serialize
     * @return JSON string representation
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON: {}", e.getMessage());
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * Serialize an object to a pretty-printed JSON string.
     *
     * @param obj the object to serialize
     * @return pretty-printed JSON string
     */
    public static String toPrettyJson(Object obj) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to pretty JSON: {}", e.getMessage());
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * Deserialize a JSON string to an object of the specified type.
     *
     * @param json the JSON string
     * @param type the target class
     * @param <T>  target type parameter
     * @return deserialized object
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to {}: {}", type.getSimpleName(), e.getMessage());
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * Convert an object to a {@code Map<String, Object>}.
     *
     * @param obj the source object
     * @return map representation
     */
    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.convertValue(obj, new TypeReference<Map<String, Object>>() {});
        } catch (IllegalArgumentException e) {
            log.error("Failed to convert object to Map: {}", e.getMessage());
            throw new RuntimeException("Object-to-Map conversion failed", e);
        }
    }
}
