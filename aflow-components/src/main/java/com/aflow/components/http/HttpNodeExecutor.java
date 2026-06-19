package com.aflow.components.http;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.core.expression.ExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 请求节点执行器。
 * <p>
 * 支持配置项：
 * <ul>
 *   <li><b>url</b> — 请求地址，支持 ${#varName} 模板变量</li>
 *   <li><b>method</b> — HTTP 方法（GET/POST/PUT/DELETE 等，默认 GET）</li>
 *   <li><b>headers</b> — 请求头键值对，值支持模板变量</li>
 *   <li><b>body</b> — 请求体（字符串类型支持模板变量解析）</li>
 * </ul>
 * <p>
 * 模板解析统一使用 {@link ExpressionEvaluator#resolveTemplate}，
 * 与条件边、赋值节点等其他节点保持一致的 SpEL 语法（${#varName}）。
 */
@FlowNode(type = "http", name = "HTTP Request", description = "发送 HTTP 请求到外部服务")
@Component
public class HttpNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpNodeExecutor.class);

    private final RestTemplate restTemplate;
    private final ExpressionEvaluator expressionEvaluator;

    public HttpNodeExecutor(RestTemplate restTemplate, ExpressionEvaluator expressionEvaluator) {
        this.restTemplate = restTemplate;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public NodeResult execute(NodeConfig config, FlowContext context) {
        Map<String, Object> cfg = config.getConfig();
        // 解析 URL 中的模板变量（如 ${#apiUrl}/users/${#userId}）
        String url = expressionEvaluator.resolveTemplate(
                String.valueOf(cfg.getOrDefault("url", "")), context);
        String method = String.valueOf(cfg.getOrDefault("method", "GET")).toUpperCase();

        try {
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (cfg.get("headers") instanceof Map<?, ?> headerMap) {
                headerMap.forEach((k, v) -> {
                    String headerValue = String.valueOf(v);
                    // 请求头值也支持模板变量解析
                    if (headerValue.contains("${")) {
                        headerValue = expressionEvaluator.resolveTemplate(headerValue, context);
                    }
                    headers.add(String.valueOf(k), headerValue);
                });
            }

            // 构建请求体（字符串类型的 body 支持模板变量解析）
            Object body = cfg.get("body");
            if (body instanceof String bodyStr && bodyStr.contains("${")) {
                body = expressionEvaluator.resolveTemplate(bodyStr, context);
            }
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.valueOf(method), entity, String.class);

            // 将响应信息存入输出
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("statusCode", response.getStatusCode().value());
            outputs.put("body", response.getBody());
            outputs.put("headers", response.getHeaders().toSingleValueMap());

            log.info("HTTP {} {} -> {}", method, url, response.getStatusCode());
            return NodeResult.success(outputs);
        } catch (Exception e) {
            log.error("HTTP 请求失败: {} {} -> {}", method, url, e.getMessage());
            return NodeResult.failed("HTTP request failed: " + e.getMessage());
        }
    }
}
