package com.aflow.agent.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 Spring AI 1.0 的 LLM 服务实现。
 * <p>
 * 使用 Spring AI 的 {@link ChatClient} 与 OpenAI 兼容 API 通信。
 * 支持 OpenAI、通义千问、智谱等兼容 API 提供商。
 * <p>
 * 此 Bean 由 {@link LlmConfiguration} 条件创建，只在配置了 LLM 提供商时激活。
 * 不要在类上标注 {@code @Service} 以避免无条件创建。
 * <p>
 * <b>配置示例（application.yml）：</b>
 * <pre>
 * spring.ai.model.chat: openai
 * spring.ai.openai:
 *   api-key: ${LLM_API_KEY}
 *   base-url: https://api.openai.com
 *   chat.options:
 *     model: gpt-4o
 *     temperature: 0.7
 *     max-tokens: 4096
 * </pre>
 *
 * @author AFlow Team
 * @since 1.0.0
 */
public class SpringAiLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmService.class);

    private final ChatClient chatClient;

    public SpringAiLlmService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String chat(List<LlmMessage> messages) {
        List<Message> springMessages = convertMessages(messages);
        return chatClient.prompt()
                .messages(springMessages)
                .call()
                .content();
    }

    @Override
    public LlmResponse chatWithTools(List<LlmMessage> messages) {
        List<Message> springMessages = convertMessages(messages);

        log.debug("调用 LLM（含工具）: messageCount={}", springMessages.size());

        long startTime = System.currentTimeMillis();
        ChatResponse response = chatClient.prompt()
                .messages(springMessages)
                .call()
                .chatResponse();
        long latencyMs = System.currentTimeMillis() - startTime;

        LlmResponse llmResponse = new LlmResponse();
        AssistantMessage output = response.getResult().getOutput();

        // 提取文本内容
        llmResponse.setContent(output.getText());
        llmResponse.setLatencyMs(latencyMs);

        // 提取 Usage 信息（token 消耗）
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            if (usage.getPromptTokens() != null && usage.getPromptTokens() > 0) {
                llmResponse.setPromptTokens(usage.getPromptTokens());
            }
            if (usage.getCompletionTokens() != null && usage.getCompletionTokens() > 0) {
                llmResponse.setCompletionTokens(usage.getCompletionTokens());
            }
        }

        // 提取模型版本
        if (response.getMetadata() != null && response.getMetadata().getModel() != null) {
            llmResponse.setModelVersion(response.getMetadata().getModel());
        }

        // 提取 Tool Call（如果有）
        if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
            List<LlmMessage.ToolCall> toolCalls = output.getToolCalls().stream()
                    .map(tc -> new LlmMessage.ToolCall(
                            tc.id(),
                            tc.name(),
                            tc.arguments() != null ? tc.arguments() : "{}"
                    ))
                    .collect(Collectors.toList());
            llmResponse.setToolCalls(toolCalls);
        }

        log.debug("LLM 响应: hasToolCalls={}, contentLength={}, promptTokens={}, completionTokens={}, model={}, latency={}ms",
                llmResponse.hasToolCalls(),
                llmResponse.getContent() != null ? llmResponse.getContent().length() : 0,
                llmResponse.getPromptTokens(),
                llmResponse.getCompletionTokens(),
                llmResponse.getModelVersion(),
                latencyMs);

        return llmResponse;
    }

    @Override
    public String getProviderName() {
        return "spring-ai";
    }

    /**
     * 将内部 LlmMessage 转换为 Spring AI Message。
     */
    private List<Message> convertMessages(List<LlmMessage> messages) {
        return messages.stream()
                .map(this::convertMessage)
                .collect(Collectors.toList());
    }

    private Message convertMessage(LlmMessage msg) {
        return switch (msg.getRole()) {
            case "system" -> new SystemMessage(msg.getContent());
            case "user" -> new UserMessage(msg.getContent());
            case "assistant" -> new AssistantMessage(msg.getContent());
            case "tool" -> new UserMessage("[Tool Result] " + msg.getContent());
            default -> new UserMessage(msg.getContent());
        };
    }
}
