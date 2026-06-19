package com.aflow.agent.llm;

import java.util.List;

/**
 * LLM 结构化响应。
 * <p>
 * 包含 LLM 的文本回复和可能的 Tool Call 请求列表。
 * 当 {@code toolCalls} 不为空时，Agent 执行器应执行这些工具并将结果反馈给 LLM。
 *
 * @author AFlow Team
 * @since 1.0.0
 */
public class LlmResponse {

    /** LLM 的文本回复 */
    private String content;

    /** LLM 请求调用的工具列表（为空时表示纯文本响应，无需调用工具） */
    private List<LlmMessage.ToolCall> toolCalls;

    // ─── Usage / Observability fields ───────────────────────────────

    /** 提示词（输入）token 数量（由 LLM 提供商返回，可能为 null） */
    private Integer promptTokens;

    /** 生成（输出）token 数量（由 LLM 提供商返回，可能为 null） */
    private Integer completionTokens;

    /** 使用的模型版本标识（如 "gpt-4o-2024-05-13"） */
    private String modelVersion;

    /** 本次 LLM 调用延迟（毫秒） */
    private long latencyMs;

    /** 是否包含 Tool Call（便捷判断） */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /** 总 token 数（prompt + completion），若任一为 null 则返回 null */
    public Integer getTotalTokens() {
        if (promptTokens == null || completionTokens == null) return null;
        return promptTokens + completionTokens;
    }

    // --- Getters & Setters ---

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<LlmMessage.ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<LlmMessage.ToolCall> toolCalls) { this.toolCalls = toolCalls; }

    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }

    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
}
