package com.aflow.agent.tool;

import java.lang.annotation.*;

/**
 * 标注在 NodeExecutor 上，表示该执行器可作为 Agent Tool 被 LLM 调用。
 * <p>
 * <b>使用示例：</b>
 * <pre>{@code
 * @Component
 * @FlowNode(type = "http", name = "HTTP 请求", description = "发送 HTTP 请求到外部 API")
 * @Tool(name = "http_request",
 *       description = "Make HTTP requests to external APIs. Use this when you need to fetch data from or send data to external services.",
 *       parameters = "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\",\"description\":\"Request URL\"},\"method\":{\"type\":\"string\",\"description\":\"HTTP method: GET, POST, PUT, DELETE\"}}}")
 * public class HttpNodeExecutor implements NodeExecutor {
 *     // ...
 * }
 * }</pre>
 *
 * @author AFlow Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Tool {

    /**
     * Tool 名称（LLM 通过此名称调用工具）。
     * 建议使用 snake_case 格式，如 "http_request"、"send_email"。
     */
    String name();

    /**
     * Tool 描述（LLM 根据此描述决定何时使用工具）。
     * 应清晰说明工具的用途和使用场景。
     */
    String description();

    /**
     * 参数 JSON Schema（LLM 根据此 Schema 生成调用参数）。
     * 必须是合法的 JSON Schema 字符串。
     */
    String parameters() default "{\"type\":\"object\",\"properties\":{}}";
}
