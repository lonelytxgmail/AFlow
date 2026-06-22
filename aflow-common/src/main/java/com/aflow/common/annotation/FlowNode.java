package com.aflow.common.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a flow node executor.
 * <p>
 * Classes annotated with {@code @FlowNode} must implement
 * {@link com.aflow.common.executor.NodeExecutor}. They are automatically
 * discovered and registered in the {@code NodeRegistry} on application startup.
 *
 * <pre>
 * &#64;FlowNode(type = "http", name = "HTTP Request", description = "Executes HTTP requests")
 * public class HttpNodeExecutor implements NodeExecutor { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface FlowNode {

    /**
     * Unique type identifier for this node (e.g., "http", "condition", "script").
     */
    String type();

    /**
     * Human-readable name for this node type.
     */
    String name() default "";

    /**
     * Description of what this node type does.
     */
    String description() default "";

    /**
     * JSON Schema describing the configuration fields this node type accepts.
     * <p>
     * The schema follows JSON Schema draft-07 with optional {@code x-} extensions for UI hints:
     * <ul>
     *   <li>{@code x-component} — UI component hint (input, select, key-value, textarea, code-editor)</li>
     *   <li>{@code x-order} — display order in forms</li>
     *   <li>{@code x-group} — logical grouping label</li>
     * </ul>
     * <p>
     * Example: {@code "{ \"properties\": { \"url\": { \"type\": \"string\" } } }"}
     * <p>
     * Defaults to empty string (no schema).
     */
    String configSchema() default "";
}
