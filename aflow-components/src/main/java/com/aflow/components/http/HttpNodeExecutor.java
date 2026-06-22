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
@FlowNode(type = "http", name = "HTTP Request", description = "发送 HTTP 请求到外部服务",
    configSchema = """
    {
      "type": "object",
      "properties": {
        "url": {
          "type": "string",
          "title": "请求地址",
          "description": "支持 SpEL 模板变量，如 ${#apiUrl}/users/${#userId}",
          "x-component": "input",
          "x-order": 1,
          "x-group": "基本配置"
        },
        "method": {
          "type": "string",
          "title": "HTTP 方法",
          "enum": ["GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"],
          "default": "GET",
          "x-component": "select",
          "x-order": 2,
          "x-group": "基本配置"
        },
        "contentType": {
          "type": "string",
          "title": "Content-Type",
          "description": "请求体内容类型",
          "enum": ["application/json", "application/x-www-form-urlencoded", "multipart/form-data", "text/plain"],
          "default": "application/json",
          "x-component": "select",
          "x-order": 3,
          "x-group": "基本配置"
        },
        "queryParams": {
          "type": "object",
          "title": "Query 参数",
          "description": "URL 查询参数，会拼接到 URL 后面（如 ?page=1&size=10）",
          "additionalProperties": { "type": "string" },
          "x-component": "key-value",
          "x-order": 4,
          "x-group": "基本配置"
        },
        "headers": {
          "type": "object",
          "title": "请求头",
          "description": "自定义请求头，值支持模板变量",
          "additionalProperties": { "type": "string" },
          "x-component": "key-value",
          "x-order": 5,
          "x-group": "基本配置"
        },
        "body": {
          "type": "string",
          "title": "请求体",
          "description": "请求体内容，支持模板变量。POST/PUT/PATCH 时生效",
          "x-component": "textarea",
          "x-order": 6,
          "x-group": "基本配置"
        },
        "authType": {
          "type": "string",
          "title": "认证方式",
          "enum": ["none", "bearer", "basic", "apiKey"],
          "default": "none",
          "x-component": "select",
          "x-order": 7,
          "x-group": "认证"
        },
        "authToken": {
          "type": "string",
          "title": "Token / API Key",
          "description": "Bearer Token 或 API Key 值，支持模板变量如 ${#apiToken}",
          "x-component": "input",
          "x-order": 8,
          "x-group": "认证"
        },
        "authUsername": {
          "type": "string",
          "title": "用户名",
          "description": "Basic Auth 用户名",
          "x-component": "input",
          "x-order": 9,
          "x-group": "认证"
        },
        "authPassword": {
          "type": "string",
          "title": "密码",
          "description": "Basic Auth 密码",
          "x-component": "input",
          "x-order": 10,
          "x-group": "认证"
        },
        "timeout": {
          "type": "integer",
          "title": "超时时间 (ms)",
          "description": "请求超时时间，单位毫秒。0 表示不限制",
          "default": 10000,
          "x-component": "number",
          "x-order": 11,
          "x-group": "高级选项"
        },
        "ignoreSslErrors": {
          "type": "boolean",
          "title": "忽略 SSL 错误",
          "description": "适用于自签证书的内网环境",
          "default": false,
          "x-component": "switch",
          "x-order": 12,
          "x-group": "高级选项"
        },
        "followRedirects": {
          "type": "boolean",
          "title": "跟随重定向",
          "description": "自动跟随 3xx 重定向",
          "default": true,
          "x-component": "switch",
          "x-order": 13,
          "x-group": "高级选项"
        },
        "retryCount": {
          "type": "integer",
          "title": "失败重试次数",
          "description": "请求失败后重试次数，0 表示不重试",
          "default": 0,
          "x-component": "number",
          "x-order": 14,
          "x-group": "高级选项"
        },
        "responseType": {
          "type": "string",
          "title": "响应格式",
          "description": "期望的响应解析方式",
          "enum": ["auto", "json", "text", "binary"],
          "default": "auto",
          "x-component": "select",
          "x-order": 15,
          "x-group": "高级选项"
        }
      },
      "required": ["url", "method"]
    }
    """
)
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
            // 拼接 Query Parameters
            if (cfg.get("queryParams") instanceof Map<?, ?> queryMap && !queryMap.isEmpty()) {
                StringBuilder queryStr = new StringBuilder();
                queryMap.forEach((k, v) -> {
                    if (queryStr.length() > 0) queryStr.append("&");
                    String val = expressionEvaluator.resolveTemplate(String.valueOf(v), context);
                    queryStr.append(k).append("=").append(val);
                });
                url += (url.contains("?") ? "&" : "?") + queryStr;
            }

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            String contentType = String.valueOf(cfg.getOrDefault("contentType", "application/json"));
            headers.setContentType(MediaType.parseMediaType(contentType));

            if (cfg.get("headers") instanceof Map<?, ?> headerMap) {
                headerMap.forEach((k, v) -> {
                    String headerValue = String.valueOf(v);
                    if (headerValue.contains("${")) {
                        headerValue = expressionEvaluator.resolveTemplate(headerValue, context);
                    }
                    headers.add(String.valueOf(k), headerValue);
                });
            }

            // 认证处理
            String authType = String.valueOf(cfg.getOrDefault("authType", "none"));
            switch (authType) {
                case "bearer" -> {
                    String token = expressionEvaluator.resolveTemplate(
                            String.valueOf(cfg.getOrDefault("authToken", "")), context);
                    headers.set("Authorization", "Bearer " + token);
                }
                case "basic" -> {
                    String username = expressionEvaluator.resolveTemplate(
                            String.valueOf(cfg.getOrDefault("authUsername", "")), context);
                    String password = expressionEvaluator.resolveTemplate(
                            String.valueOf(cfg.getOrDefault("authPassword", "")), context);
                    String encoded = java.util.Base64.getEncoder()
                            .encodeToString((username + ":" + password).getBytes());
                    headers.set("Authorization", "Basic " + encoded);
                }
                case "apiKey" -> {
                    String apiKey = expressionEvaluator.resolveTemplate(
                            String.valueOf(cfg.getOrDefault("authToken", "")), context);
                    headers.set("X-API-Key", apiKey);
                }
                default -> { /* none */ }
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
