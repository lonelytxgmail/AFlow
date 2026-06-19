package com.aflow.agent.llm;

import java.util.List;

/**
 * LLM 服务抽象层。
 * <p>
 * 定义与大语言模型通信的标准接口，屏蔽不同 LLM 提供商（OpenAI / 通义千问 / 智谱 / 本地模型）的差异。
 * Agent 执行器通过此接口与 LLM 交互，不直接依赖具体实现。
 * <p>
 * <b>职责：</b>
 * <ul>
 *   <li>发送 Prompt 并获取 LLM 响应</li>
 *   <li>处理 Tool Call 请求（LLM 决定调用哪些工具）</li>
 *   <li>管理对话上下文（多轮交互）</li>
 * </ul>
 *
 * @author AFlow Team
 * @since 1.0.0
 */
public interface LlmService {

    /**
     * 向 LLM 发送消息列表并获取响应。
     *
     * @param messages 对话消息列表（包含 System Prompt、用户输入、工具调用结果等）
     * @return LLM 的文本响应
     * @throws LlmException 当 LLM 调用失败时抛出
     */
    String chat(List<LlmMessage> messages);

    /**
     * 向 LLM 发送消息并获取结构化响应（可能包含文本 + Tool Call）。
     *
     * @param messages 对话消息列表
     * @return 结构化响应，包含文本内容和可能的 Tool Call 列表
     * @throws LlmException 当 LLM 调用失败时抛出
     */
    LlmResponse chatWithTools(List<LlmMessage> messages);

    /**
     * LLM 提供商名称（如 "openai"、"qwen"、"zhipu"）。
     *
     * @return 提供商标识
     */
    String getProviderName();
}
