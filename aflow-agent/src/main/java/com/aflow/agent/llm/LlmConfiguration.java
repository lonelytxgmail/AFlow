package com.aflow.agent.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 服务配置。
 * <p>
 * 根据 {@code spring.ai.model.chat} 配置决定注入哪个 {@link LlmService} 实现：
 * <ul>
 *   <li>{@code none} 或未设置 → {@link DisabledLlmService}（Agent 节点执行时报错）</li>
 *   <li>其他值（如 {@code openai}） → {@link SpringAiLlmService}（真实 LLM 调用）</li>
 * </ul>
 * <p>
 * 当 LLM 重试配置启用时（{@code maxAttempts > 1}），自动使用 {@link RetryingLlmService}
 * 装饰器包装实际的 LLM 服务，提供瞬态故障重试能力。
 * <p>
 * 采用单 Bean 方法 + 程序化路由，避免条件注解与 Spring AI 自动配置的交互问题。
 *
 * @author AFlow Team
 * @since 1.0.0
 */
@Configuration
public class LlmConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LlmConfiguration.class);

    @Value("${spring.ai.model.chat:none}")
    private String chatModel;

    @Value("${aflow.agent.llm.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${aflow.agent.llm.retry.initial-backoff-ms:1000}")
    private long initialBackoffMs;

    @Value("${aflow.agent.llm.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    @Bean
    public LlmService llmService(@Autowired(required = false) ChatClient.Builder chatClientBuilder) {
        LlmService service = createBaseLlmService(chatClientBuilder);
        return wrapWithRetryIfNeeded(service);
    }

    /**
     * 根据配置创建基础 LLM 服务实例。
     */
    private LlmService createBaseLlmService(ChatClient.Builder chatClientBuilder) {
        if (chatModel == null || "none".equals(chatModel)) {
            log.info("LLM 未配置 (spring.ai.model.chat=none)，使用 DisabledLlmService");
            return new DisabledLlmService();
        }

        if (chatClientBuilder == null) {
            log.error("LLM 配置了模型 '{}' 但 ChatClient.Builder 不可用，"
                    + "请确保正确配置了 Spring AI 提供商", chatModel);
            return new DisabledLlmService();
        }

        log.info("LLM 已配置: model={}, 使用 SpringAiLlmService", chatModel);
        return new SpringAiLlmService(chatClientBuilder);
    }

    /**
     * 根据重试配置决定是否使用 {@link RetryingLlmService} 装饰器包装。
     * <p>
     * 不包装的情况：
     * <ul>
     *   <li>{@code maxAttempts <= 1}（重试已禁用）</li>
     *   <li>底层服务为 {@link DisabledLlmService}（无需重试一个不可用的服务）</li>
     * </ul>
     */
    private LlmService wrapWithRetryIfNeeded(LlmService service) {
        if (service instanceof DisabledLlmService) {
            log.debug("跳过 LLM 重试装饰：底层服务为 DisabledLlmService");
            return service;
        }

        if (maxAttempts <= 1) {
            log.info("LLM 重试已禁用 (max-attempts={})", maxAttempts);
            return service;
        }

        log.info("LLM 重试已启用: maxAttempts={}, initialBackoffMs={}, backoffMultiplier={}",
                maxAttempts, initialBackoffMs, backoffMultiplier);
        return new RetryingLlmService(service, maxAttempts, initialBackoffMs, backoffMultiplier);
    }
}
