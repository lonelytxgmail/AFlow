package com.aflow.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM 对话消息。
 * <p>
 * 表示发送给 LLM 的单条消息，包含角色（system/user/assistant/tool）和内容。
 * Tool Call 结果通过 {@code toolCalls} 和 {@code toolCallId} 字段传递。
 *
 * @author AFlow Team
 * @since 1.0.0
 */
public class LlmMessage {

    /** 消息角色：system / user / assistant / tool */
    private String role;

    /** 消息文本内容 */
    private String content;

    /** Tool Call 请求列表（LLM 返回的工具调用指令） */
    private List<ToolCall> toolCalls;

    /** Tool Call 结果的关联 ID（role=tool 时必填） */
    private String toolCallId;

    public LlmMessage() {}

    public LlmMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content);
    }

    public static LlmMessage toolResult(String toolCallId, String content) {
        LlmMessage msg = new LlmMessage("tool", content);
        msg.setToolCallId(toolCallId);
        return msg;
    }

    // --- Getters & Setters ---

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    /**
     * LLM 请求调用的工具。
     */
    public static class ToolCall {
        /** 调用 ID（LLM 生成的唯一标识，用于关联响应） */
        private String id;
        /** 工具名称 */
        private String name;
        /** 工具参数（JSON 字符串） */
        private String arguments;

        public ToolCall() {}

        public ToolCall(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getArguments() { return arguments; }
        public void setArguments(String arguments) { this.arguments = arguments; }
    }
}
