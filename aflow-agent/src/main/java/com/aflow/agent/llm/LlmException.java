package com.aflow.agent.llm;

/**
 * LLM 调用异常。
 * <p>
 * 当 LLM 服务调用失败（网络错误、认证失败、速率限制等）时抛出。
 *
 * @author AFlow Team
 * @since 1.0.0
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
