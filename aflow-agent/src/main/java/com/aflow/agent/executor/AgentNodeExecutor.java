package com.aflow.agent.executor;

import com.aflow.agent.llm.LlmMessage;
import com.aflow.agent.llm.LlmResponse;
import com.aflow.agent.llm.LlmService;
import com.aflow.agent.tool.NodeExecutorToolAdapter;
import com.aflow.agent.tool.Tool;
import com.aflow.agent.tool.ToolRegistry;
import com.aflow.agent.tool.ToolSelector;
import com.aflow.common.annotation.FlowNode;
import com.aflow.common.exception.FlowExecutionException;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.core.event.EventPersistenceService;
import com.aflow.core.event.FlowEventBus;
import com.aflow.core.event.FlowEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Agent 节点执行器 —— 基于 ReAct（Reasoning + Acting）模式。
 * <p>
 * <b>执行流程：</b>
 * <pre>
 * ┌──────────────────────────────────────────────────────────┐
 * │ 1. 构建 System Prompt（含可用工具描述 + 用户指令）        │
 * │ 2. 进入循环（最大 N 轮）:                                │
 * │    ├─ 调用 LLM → 获取响应                               │
 * │    ├─ 如果响应包含 Tool Call:                            │
 * │    │   ├─ 执行对应 Tool                                  │
 * │    │   ├─ 将结果追加到消息历史                            │
 * │    │   └─ 继续循环                                       │
 * │    └─ 如果响应是纯文本:                                  │
 * │        └─ 返回结果，结束循环                              │
 * │ 3. 将最终结果写入流程上下文                               │
 * </pre>
 * <p>
 * <b>配置参数（NodeConfig.config）：</b>
 * <ul>
 *   <li><code>userPrompt</code> — 用户指令（必填），支持 ${#varName} 模板</li>
 *   <li><code>systemPrompt</code> — 系统提示词（可选），覆盖默认 System Prompt</li>
 *   <li><code>outputVariable</code> — 输出变量名（默认 "agent_result"）</li>
 *   <li><code>maxIterations</code> — 最大循环次数（默认 10，防止无限循环）</li>
 * </ul>
 *
 * @author AFlow Team
 * @since 1.0.0
 */
@Component
@FlowNode(type = "agent", name = "Agent 节点", description = "基于 ReAct 模式的 AI Agent 执行器，可调用 LLM 和工具完成复杂任务")
@Tool(name = "agent",
      description = "AI Agent that can reason and use tools to complete complex tasks",
      parameters = "{\"type\":\"object\",\"properties\":{\"userPrompt\":{\"type\":\"string\",\"description\":\"User instruction for the agent\"}}}")
public class AgentNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentNodeExecutor.class);
    private static final String DEFAULT_OUTPUT_VARIABLE = "agent_result";
    private static final int DEFAULT_MAX_ITERATIONS = 10;
    private static final int DEFAULT_TEMPERATURE = 0;
    private static final long DEFAULT_TOOL_TIMEOUT_MS = 30000;
    private static final int DEFAULT_TOOL_RESULT_MAX_LENGTH = 4000;
    private static final int DEFAULT_MAX_TOKEN_BUDGET = 100000;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a helpful AI assistant that can use tools to complete tasks.
            You should reason step by step, use tools when needed, and provide clear answers.
            When you have enough information to answer the user's question, provide your final answer.
            """;
    private static final int TOOL_RESULT_PREVIEW_LIMIT = 200;

    private final LlmService llmService;
    private final ObjectProvider<ToolRegistry> toolRegistryProvider;
    private final EventPersistenceService eventPersistenceService;
    private final FlowEventBus eventBus;
    private final ObjectMapper objectMapper;
    private final ExecutorService virtualThreadExecutor;
    private final ToolSelector toolSelector;

    public AgentNodeExecutor(LlmService llmService,
                             ObjectProvider<ToolRegistry> toolRegistryProvider,
                             EventPersistenceService eventPersistenceService,
                             FlowEventBus eventBus,
                             ObjectMapper objectMapper,
                             ToolSelector toolSelector) {
        this.llmService = llmService;
        this.toolRegistryProvider = toolRegistryProvider;
        this.eventPersistenceService = eventPersistenceService;
        this.eventBus = eventBus;
        this.objectMapper = objectMapper;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.toolSelector = toolSelector;
    }

    @Override
    public NodeResult execute(NodeConfig config, FlowContext context) {
        Map<String, Object> cfg = config.getConfig();
        if (cfg == null) {
            throw new FlowExecutionException("Agent 节点配置为空");
        }

        String userPrompt = resolveTemplate(getConfigValue(cfg, "userPrompt", ""), context);
        String systemPrompt = resolveTemplate(getConfigValue(cfg, "systemPrompt", DEFAULT_SYSTEM_PROMPT), context);
        String outputVariable = getConfigValue(cfg, "outputVariable", DEFAULT_OUTPUT_VARIABLE);
        int maxIterations = Integer.parseInt(getConfigValue(cfg, "maxIterations",
                String.valueOf(DEFAULT_MAX_ITERATIONS)));
        String model = getConfigValue(cfg, "model", "");
        String temperature = getConfigValue(cfg, "temperature", String.valueOf(DEFAULT_TEMPERATURE));

        long toolTimeoutMs = Long.parseLong(getConfigValue(cfg, "toolTimeout",
                String.valueOf(DEFAULT_TOOL_TIMEOUT_MS)));
        int toolResultMaxLength = Integer.parseInt(getConfigValue(cfg, "toolResultMaxLength",
                String.valueOf(DEFAULT_TOOL_RESULT_MAX_LENGTH)));
        int maxTokenBudget = Integer.parseInt(getConfigValue(cfg, "maxTokenBudget",
                String.valueOf(DEFAULT_MAX_TOKEN_BUDGET)));

        ToolResultTruncator toolResultTruncator = new ToolResultTruncator(toolResultMaxLength);
        TokenBudgetManager tokenBudgetManager = new TokenBudgetManager(maxTokenBudget);
        ToolRateLimiter toolRateLimiter = parseToolRateLimiter(cfg.get("toolRateLimit"));

        if (userPrompt.isEmpty()) {
            throw new FlowExecutionException("Agent 节点的 userPrompt 不能为空");
        }

        log.info("Agent 开始执行: maxIterations={}, outputVariable={}, model={}",
                maxIterations, outputVariable, model);

        ToolRegistry toolRegistry = toolRegistryProvider.getObject();
        List<NodeExecutorToolAdapter> allowedTools = toolSelector.selectTools(cfg.get("tools"), context, toolRegistry);
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(systemPrompt + "\n\n" + toolRegistry.buildToolDescriptions(allowedTools)));
        messages.add(LlmMessage.user(userPrompt));

        String finalAnswer = null;
        int totalLlmCalls = 0;
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        long totalLlmLatencyMs = 0;
        String lastModelVersion = null;

        for (int i = 0; i < maxIterations; i++) {
            log.debug("Agent 迭代 {}/{}", i + 1, maxIterations);
            publishAgentEvent(context, FlowEventType.AGENT_THINK, Map.of(
                    "iteration", i + 1,
                    "maxIterations", maxIterations,
                    "messageCount", messages.size()
            ));

            LlmResponse response;
            try {
                messages = tokenBudgetManager.pruneIfNeeded(messages, eventBus, context);
                response = llmService.chatWithTools(messages);
            } catch (Exception e) {
                log.error("Agent LLM 调用失败: iteration={}, error={}", i + 1, e.getMessage());
                throw new FlowExecutionException("Agent LLM 调用失败: " + e.getMessage(), e);
            }

            // 累计 LLM 调用指标
            totalLlmCalls++;
            totalLlmLatencyMs += response.getLatencyMs();
            if (response.getPromptTokens() != null) totalPromptTokens += response.getPromptTokens();
            if (response.getCompletionTokens() != null) totalCompletionTokens += response.getCompletionTokens();
            if (response.getModelVersion() != null) lastModelVersion = response.getModelVersion();

            if (response.hasToolCalls()) {
                LlmMessage assistantMsg = LlmMessage.assistant(response.getContent());
                assistantMsg.setToolCalls(response.getToolCalls());
                messages.add(assistantMsg);

                for (LlmMessage.ToolCall toolCall : response.getToolCalls()) {
                    log.debug("Agent 执行 Tool: name={}, id={}", toolCall.getName(), toolCall.getId());
                    publishAgentEvent(context, FlowEventType.AGENT_ACT, Map.of(
                            "iteration", i + 1,
                            "toolName", toolCall.getName(),
                            "toolCallId", toolCall.getId(),
                            "arguments", toolCall.getArguments(),
                            "model", model,
                            "temperature", temperature
                    ));

                    NodeExecutorToolAdapter tool = toolRegistry.getTool(toolCall.getName());
                    String toolResult;

                    // 频率限制检查
                    if (toolRateLimiter.isEnabled() && !toolRateLimiter.checkAndIncrement(toolCall.getName())) {
                        toolResult = toolRateLimiter.getRejectionMessage(toolCall.getName());
                        publishAgentEvent(context, FlowEventType.AGENT_TOOL_RATE_LIMITED, Map.of(
                                "iteration", i + 1,
                                "toolName", toolCall.getName(),
                                "toolCallId", toolCall.getId(),
                                "callCount", toolRateLimiter.getCallCount(toolCall.getName()),
                                "totalCalls", toolRateLimiter.getTotalCalls()
                        ));
                        log.info("Agent Tool 频率限制: name={}, perToolCount={}, totalCalls={}",
                                toolCall.getName(), toolRateLimiter.getCallCount(toolCall.getName()), toolRateLimiter.getTotalCalls());
                    } else if (tool != null) {
                        try {
                            toolResult = CompletableFuture.supplyAsync(
                                    () -> tool.execute(toolCall.getArguments(), context), virtualThreadExecutor
                            ).get(toolTimeoutMs, TimeUnit.MILLISECONDS);
                        } catch (TimeoutException e) {
                            toolResult = "Tool execution timed out after " + toolTimeoutMs + "ms";
                            publishAgentEvent(context, FlowEventType.AGENT_TOOL_TIMEOUT, Map.of(
                                    "toolName", toolCall.getName(),
                                    "toolCallId", toolCall.getId(),
                                    "timeoutMs", toolTimeoutMs
                            ));
                            log.warn("Agent Tool 执行超时: name={}, timeoutMs={}", toolCall.getName(), toolTimeoutMs);
                        } catch (Exception e) {
                            toolResult = "Tool execution error: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                            log.warn("Agent Tool 执行异常: name={}, error={}", toolCall.getName(), e.getMessage());
                        }
                    } else {
                        toolResult = "Tool '" + toolCall.getName() + "' not found";
                        log.warn("Agent Tool 不存在: name={}", toolCall.getName());
                    }

                    // Publish full result via AGENT_OBSERVE event before truncation
                    publishAgentEvent(context, FlowEventType.AGENT_OBSERVE, Map.of(
                            "iteration", i + 1,
                            "toolName", toolCall.getName(),
                            "toolCallId", toolCall.getId(),
                            "resultPreview", truncate(toolResult, TOOL_RESULT_PREVIEW_LIMIT)
                    ));

                    // Truncate before adding to message history for LLM
                    String truncatedResult = toolResultTruncator.truncate(toolResult);
                    log.debug("Tool result truncated: name={}, originalLen={}, truncatedLen={}",
                            toolCall.getName(), toolResult.length(), truncatedResult.length());

                    messages.add(LlmMessage.toolResult(toolCall.getId(), truncatedResult));
                }
            } else {
                finalAnswer = response.getContent();
                log.info("Agent 获得最终答案: length={}", finalAnswer != null ? finalAnswer.length() : 0);
                break;
            }
        }

        if (finalAnswer == null) {
            log.warn("Agent 达到最大迭代次数: maxIterations={}", maxIterations);
            finalAnswer = "Agent reached maximum iterations (" + maxIterations + ") without a final answer.";
        }

        // Output Guardrails: 验证 Agent 输出是否符合指定 schema
        String outputSchemaJson = getConfigValue(cfg, "outputSchema", "");
        String outputValidation = getConfigValue(cfg, "outputValidation", "passthrough");
        if (!outputSchemaJson.isEmpty()) {
            try {
                OutputSchemaValidator validator = new OutputSchemaValidator(outputSchemaJson);
                OutputSchemaValidator.ValidationResult validationResult = validator.validate(finalAnswer);

                if (!validationResult.valid()) {
                    log.warn("Agent 输出校验失败: errors={}", validationResult.errors());
                    publishAgentEvent(context, FlowEventType.AGENT_OUTPUT_VALIDATION_FAILED, Map.of(
                            "errors", validationResult.errors(),
                            "validation", outputValidation,
                            "outputPreview", truncate(finalAnswer, TOOL_RESULT_PREVIEW_LIMIT)
                    ));

                    switch (outputValidation) {
                        case "retry" -> {
                            // 追加修正指令让 LLM 再来一轮
                            messages.add(LlmMessage.assistant(finalAnswer));
                            messages.add(LlmMessage.user(
                                    "Your previous output does not conform to the required JSON schema. " +
                                    "Errors: " + String.join("; ", validationResult.errors()) + ". " +
                                    "Please output a corrected JSON response that matches the schema."));
                            try {
                                LlmResponse correctionResponse = llmService.chatWithTools(messages);
                                if (correctionResponse.getContent() != null && !correctionResponse.getContent().isBlank()) {
                                    finalAnswer = correctionResponse.getContent();
                                    totalLlmCalls++;
                                    totalLlmLatencyMs += correctionResponse.getLatencyMs();
                                    if (correctionResponse.getPromptTokens() != null) totalPromptTokens += correctionResponse.getPromptTokens();
                                    if (correctionResponse.getCompletionTokens() != null) totalCompletionTokens += correctionResponse.getCompletionTokens();
                                }
                            } catch (Exception e) {
                                log.warn("Agent 输出校验重试失败: {}", e.getMessage());
                            }
                        }
                        case "fail" -> {
                            finalAnswer = null; // 将导致返回 FAILED
                        }
                        default -> {
                            // "passthrough" — 原样输出，已记录 WARN 事件
                        }
                    }

                    if (finalAnswer == null) {
                        return NodeResult.failed("Agent output does not match required schema: " +
                                String.join("; ", validationResult.errors()));
                    }
                }
            } catch (Exception e) {
                log.warn("Output schema 解析失败，跳过验证: {}", e.getMessage());
            }
        }

        context.getVariables().put(outputVariable, finalAnswer);
        Map<String, Object> doneData = new HashMap<>();
        doneData.put("outputVariable", outputVariable);
        doneData.put("answerLength", finalAnswer.length());
        doneData.put("finalAnswerPreview", truncate(finalAnswer, TOOL_RESULT_PREVIEW_LIMIT));
        doneData.put("totalLlmCalls", totalLlmCalls);
        doneData.put("totalPromptTokens", totalPromptTokens);
        doneData.put("totalCompletionTokens", totalCompletionTokens);
        doneData.put("totalTokens", totalPromptTokens + totalCompletionTokens);
        doneData.put("totalLlmLatencyMs", totalLlmLatencyMs);
        if (lastModelVersion != null) doneData.put("modelVersion", lastModelVersion);
        publishAgentEvent(context, FlowEventType.AGENT_DONE, doneData);

        return NodeResult.success(Map.of(outputVariable, finalAnswer));
    }

    private String getConfigValue(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private List<String> parseToolNames(Object toolsConfig) {
        if (toolsConfig == null) {
            return List.of();
        }
        if (toolsConfig instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toList());
        }
        String value = String.valueOf(toolsConfig);
        if (value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private ToolRateLimiter parseToolRateLimiter(Object rateLimitConfig) {
        if (rateLimitConfig == null) {
            return ToolRateLimiter.unlimited();
        }
        if (rateLimitConfig instanceof Map<?, ?> map) {
            int maxCallsPerTool = map.containsKey("maxCallsPerTool")
                    ? Integer.parseInt(String.valueOf(map.get("maxCallsPerTool"))) : 0;
            int maxTotalCalls = map.containsKey("maxTotalCalls")
                    ? Integer.parseInt(String.valueOf(map.get("maxTotalCalls"))) : 0;
            return new ToolRateLimiter(maxCallsPerTool, maxTotalCalls);
        }
        return ToolRateLimiter.unlimited();
    }

    private String resolveTemplate(String template, FlowContext context) {
        if (template == null || template.isEmpty()) return template;
        String result = template;
        for (Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
            result = result.replace("${#" + entry.getKey() + "}",
                    entry.getValue() != null ? String.valueOf(entry.getValue()) : "");
        }
        return result;
    }

    private String truncate(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...";
    }

    private void publishAgentEvent(FlowContext context, FlowEventType eventType, Map<String, Object> data) {
        try {
            eventBus.publish(context.getFlowInstanceId(), eventType.name(), data);
            eventPersistenceService.saveEvent(context.getFlowInstanceId(), null,
                    eventType, objectMapper.writeValueAsString(data), 0);
        } catch (Exception e) {
            log.debug("Agent 事件发布失败: event={}", eventType.name());
        }
    }
}
