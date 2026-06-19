package com.aflow.components.llm;

import com.aflow.agent.llm.LlmMessage;
import com.aflow.agent.llm.LlmService;
import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.core.expression.ExpressionEvaluator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 独立 LLM 节点执行器。
 * <p>
 * 与 AgentNodeExecutor 不同，LlmNodeExecutor 仅执行单次 LLM 调用（非 ReAct 循环），
 * 适用于 Prompt Chaining 场景。
 * <p>
 * 支持配置项：
 * <ul>
 *   <li><b>model</b> — 模型名称（如 gpt-4o, gpt-4o-mini）</li>
 *   <li><b>systemPrompt</b> — 系统提示词</li>
 *   <li><b>userPrompt</b> — 用户提示词模板，支持 #{variable} 插值</li>
 *   <li><b>temperature</b> — 温度参数（0~2，默认 0）</li>
 *   <li><b>outputSchema</b> — 输出 JSON Schema（可选，用于结构化输出）</li>
 * </ul>
 * <p>
 * 变量插值语法：
 * <ul>
 *   <li>{@code #{variableName}} — 从 FlowContext 变量中取值</li>
 *   <li>支持嵌套：{@code #{nodeA_output.field}}</li>
 * </ul>
 */
@FlowNode(type = "llm", name = "LLM Call", description = "单次 LLM 调用（非 Agent 循环）")
@Component
public class LlmNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(LlmNodeExecutor.class);

    /** Pattern for #{variable} template placeholders in prompts. */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("#\\{([^}]+)}");

    private final LlmService llmService;
    private final ExpressionEvaluator expressionEvaluator;
    private final ObjectMapper objectMapper;

    public LlmNodeExecutor(LlmService llmService,
                           ExpressionEvaluator expressionEvaluator,
                           ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.expressionEvaluator = expressionEvaluator;
        this.objectMapper = objectMapper;
    }

    @Override
    public NodeResult execute(NodeConfig config, FlowContext context) {
        Map<String, Object> cfg = config.getConfig();

        String systemPrompt = resolveVariables(
                String.valueOf(cfg.getOrDefault("systemPrompt", "")), context);
        String userPrompt = resolveVariables(
                String.valueOf(cfg.getOrDefault("userPrompt", "")), context);
        String outputSchemaStr = cfg.get("outputSchema") != null
                ? String.valueOf(cfg.get("outputSchema")) : null;

        if (userPrompt.isBlank()) {
            return NodeResult.failed("userPrompt is required for LLM node");
        }

        try {
            // Build messages
            List<LlmMessage> messages = new ArrayList<>();
            if (!systemPrompt.isBlank()) {
                // If outputSchema is specified, append instruction to system prompt
                String finalSystemPrompt = systemPrompt;
                if (outputSchemaStr != null && !outputSchemaStr.isBlank()) {
                    finalSystemPrompt += "\n\nYou MUST respond with valid JSON matching this schema:\n" + outputSchemaStr;
                }
                messages.add(LlmMessage.system(finalSystemPrompt));
            } else if (outputSchemaStr != null && !outputSchemaStr.isBlank()) {
                messages.add(LlmMessage.system(
                        "You MUST respond with valid JSON matching this schema:\n" + outputSchemaStr));
            }
            messages.add(LlmMessage.user(userPrompt));

            // Execute single LLM call
            String response = llmService.chat(messages);

            // Build outputs
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("response", response);

            // If outputSchema is specified, try to parse as structured JSON
            if (outputSchemaStr != null && !outputSchemaStr.isBlank()) {
                try {
                    Map<String, Object> structured = objectMapper.readValue(
                            response, new TypeReference<>() {});
                    outputs.put("structured", structured);
                } catch (Exception parseEx) {
                    log.warn("LLM response is not valid JSON despite outputSchema: {}", parseEx.getMessage());
                    // Still return the raw response, just without structured field
                }
            }

            log.info("LLM node executed successfully, response length={}", response.length());
            return NodeResult.success(outputs);
        } catch (Exception e) {
            log.error("LLM node execution failed: {}", e.getMessage());
            return NodeResult.failed("LLM call failed: " + e.getMessage());
        }
    }

    /**
     * Resolve #{variable} placeholders in a template string using the flow context.
     * Falls back to SpEL evaluation for complex expressions.
     */
    private String resolveVariables(String template, FlowContext context) {
        if (template == null || !template.contains("#{")) {
            return template != null ? template : "";
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varExpression = matcher.group(1).trim();
            try {
                // Try direct variable lookup first
                Object value = context.getVariables().get(varExpression);
                if (value == null) {
                    // Fall back to SpEL evaluation for nested expressions
                    value = expressionEvaluator.evaluate(varExpression, context, Object.class);
                }
                String replacement = value != null ? value.toString() : "";
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            } catch (Exception e) {
                log.warn("Failed to resolve variable '{}': {}", varExpression, e.getMessage());
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
