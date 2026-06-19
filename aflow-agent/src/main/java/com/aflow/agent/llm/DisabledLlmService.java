package com.aflow.agent.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * LLM 服务禁用实现。
 * <p>
 * 当未配置 LLM 提供商（spring.ai.model.chat=none）时使用此实现。
 * 所有调用方法均抛出 {@link LlmException}，提示用户配置 LLM 后再使用 Agent 功能。
 * <p>
 * 此设计允许后端在无 LLM Key 的情况下正常启动，
 * Agent 节点只有在实际执行时才会因为未配置而明确报错。
 *
 * @author AFlow Team
 * @since 1.0.0
 */
public class DisabledLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(DisabledLlmService.class);

    private static final String ERROR_MESSAGE =
            "LLM 服务未配置。请在 application.yml 中设置 spring.ai.model.chat 为有效的模型名称（如 openai），"
                    + "并配置对应的 API Key（如 spring.ai.openai.api-key）。"
                    + "开发环境下默认禁用 LLM，Agent 节点不可用。";

    @Override
    public String chat(List<LlmMessage> messages) {
        log.warn("LLM 未配置，chat 调用被拒绝");
        throw new LlmException(ERROR_MESSAGE);
    }

    @Override
    public LlmResponse chatWithTools(List<LlmMessage> messages) {
        log.warn("LLM 未配置，chatWithTools 调用被拒绝");
        throw new LlmException(ERROR_MESSAGE);
    }

    @Override
    public String getProviderName() {
        return "disabled";
    }
}
