package com.aflow.agent.tool;

import com.aflow.common.model.FlowContext;
import com.aflow.core.expression.ExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 默认 Tool 选择器实现。
 * <p>
 * 支持三种 tools 配置模式：
 * <ul>
 *   <li><b>null / 空 / "*"</b>：暴露所有已发布 Tool</li>
 *   <li><b>静态列表</b>：Collection 或逗号分隔字符串 → 按名称筛选</li>
 *   <li><b>SpEL 表达式</b>：以 "${" 开头的字符串 → 动态求值得到 Tool 名称列表</li>
 * </ul>
 *
 * @author AFlow Team
 * @since 1.1.0
 */
@Component
public class DefaultToolSelector implements ToolSelector {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolSelector.class);
    private static final String WILDCARD = "*";
    private static final String SPEL_PREFIX = "${";

    private final ExpressionEvaluator expressionEvaluator;

    public DefaultToolSelector(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public List<NodeExecutorToolAdapter> selectTools(Object toolsConfig, FlowContext context, ToolRegistry registry) {
        List<String> toolNames = resolveToolNames(toolsConfig, context);

        if (toolNames == null || toolNames.isEmpty()) {
            // null / empty / wildcard → all tools
            return registry.getAllTools();
        }

        return registry.getToolsByNames(toolNames);
    }

    /**
     * 将 tools 配置解析为 Tool 名称列表。
     *
     * @return Tool 名称列表，或 null 表示全量
     */
    private List<String> resolveToolNames(Object toolsConfig, FlowContext context) {
        if (toolsConfig == null) {
            return null; // all tools
        }

        // Collection (List/Array) — 静态列表
        if (toolsConfig instanceof Collection<?> collection) {
            List<String> names = collection.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .filter(v -> !v.isBlank())
                    .collect(Collectors.toList());
            return names.isEmpty() ? null : names;
        }

        String value = String.valueOf(toolsConfig).trim();

        // Wildcard
        if (value.isEmpty() || WILDCARD.equals(value)) {
            return null;
        }

        // SpEL expression: ${...}
        if (value.startsWith(SPEL_PREFIX) && value.endsWith("}")) {
            return evaluateSpelToolExpression(value, context);
        }

        // Comma-separated string
        List<String> names = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
        return names.isEmpty() ? null : names;
    }

    /**
     * 解析 SpEL 表达式并返回 Tool 名称列表。
     * 表达式可返回 List<String> 或逗号分隔字符串。
     */
    @SuppressWarnings("unchecked")
    private List<String> evaluateSpelToolExpression(String expression, FlowContext context) {
        // Strip ${ and }
        String spelExpr = expression.substring(2, expression.length() - 1).trim();

        try {
            Object result = expressionEvaluator.evaluate(spelExpr, context, Object.class);

            if (result == null) {
                return null;
            }

            if (result instanceof Collection<?> collection) {
                return collection.stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .filter(v -> !v.isBlank())
                        .collect(Collectors.toList());
            }

            // Single string or comma-separated
            String strResult = String.valueOf(result).trim();
            if (strResult.isEmpty() || WILDCARD.equals(strResult)) {
                return null;
            }
            return Arrays.stream(strResult.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .toList();

        } catch (Exception e) {
            log.warn("动态 Tool 选择 SpEL 表达式执行失败: expression='{}', error={}", spelExpr, e.getMessage());
            // 失败时降级为全量 Tool
            return null;
        }
    }
}
