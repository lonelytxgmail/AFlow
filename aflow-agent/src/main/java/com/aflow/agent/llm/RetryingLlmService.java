package com.aflow.agent.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 调用重试装饰器。
 * <p>
 * 包装一个 {@link LlmService} 委托，在遇到瞬态失败（5xx、429、超时、连接重置）时
 * 进行指数退避重试。对于非瞬态失败（4xx，除 429 外）立即传播异常。
 * <p>
 * 使用装饰器模式，不修改原始 {@link LlmService} 实现（开闭原则）。
 * 基于虚拟线程安全的 {@code Thread.sleep()} 实现退避等待。
 *
 * @author AFlow Team
 * @since 1.0.0
 */
public class RetryingLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(RetryingLlmService.class);

    /**
     * Pattern to extract HTTP status codes from exception messages.
     * Matches patterns like "status 503", "HTTP 429", "status code: 500", "statusCode=429"
     */
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile(
            "(?:status[_ ]?(?:code)?[=:_ ]?)(\\d{3})|(?:HTTP[/ ]?)(\\d{3})",
            Pattern.CASE_INSENSITIVE
    );

    private final LlmService delegate;
    private final int maxAttempts;
    private final long initialBackoffMs;
    private final double backoffMultiplier;

    /**
     * 创建重试装饰器。
     *
     * @param delegate          被包装的 LLM 服务
     * @param maxAttempts       最大尝试次数（包含首次调用，最小为 1）
     * @param initialBackoffMs  首次重试前的退避时间（毫秒）
     * @param backoffMultiplier 退避时间的指数乘数
     */
    public RetryingLlmService(LlmService delegate, int maxAttempts, long initialBackoffMs, double backoffMultiplier) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate LlmService must not be null");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, got: " + maxAttempts);
        }
        this.delegate = delegate;
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
    }

    @Override
    public String chat(List<LlmMessage> messages) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return delegate.chat(messages);
            } catch (LlmException e) {
                handleFailure(e, attempt);
            }
        }
    }

    @Override
    public LlmResponse chatWithTools(List<LlmMessage> messages) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return delegate.chatWithTools(messages);
            } catch (LlmException e) {
                handleFailure(e, attempt);
            }
        }
    }

    @Override
    public String getProviderName() {
        return delegate.getProviderName();
    }

    /**
     * 处理 LLM 调用失败：判断是否为瞬态故障并决定是否重试。
     *
     * @param e       捕获的异常
     * @param attempt 当前尝试次数（从 1 开始）
     * @throws LlmException 当异常为非瞬态或重试次数已用尽时抛出
     */
    private void handleFailure(LlmException e, int attempt) {
        if (!isTransient(e) || attempt >= maxAttempts) {
            throw enriched(e, attempt);
        }
        long backoff = computeBackoff(attempt);
        log.warn("LLM transient failure (attempt {}/{}), retrying in {}ms: {}",
                attempt, maxAttempts, backoff, e.getMessage());
        sleep(backoff);
    }

    /**
     * 判断异常是否为瞬态故障（可重试）。
     * <p>
     * 瞬态故障包括：
     * <ul>
     *   <li>HTTP 5xx（服务端错误）</li>
     *   <li>HTTP 429（速率限制）</li>
     *   <li>超时（timeout）</li>
     *   <li>连接重置（connection reset）</li>
     * </ul>
     * <p>
     * 非瞬态故障（立即传播）：
     * <ul>
     *   <li>HTTP 400（Bad Request）</li>
     *   <li>HTTP 401（Unauthorized）</li>
     *   <li>HTTP 403（Forbidden）</li>
     *   <li>HTTP 422（Unprocessable Entity）</li>
     * </ul>
     *
     * @param e LLM 异常
     * @return true 表示瞬态可重试，false 表示非瞬态应立即失败
     */
    boolean isTransient(LlmException e) {
        String message = e.getMessage();
        if (message == null) {
            message = "";
        }
        String lowerMessage = message.toLowerCase();

        // Check for timeout or connection reset keywords
        if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out")) {
            return true;
        }
        if (lowerMessage.contains("connection reset") || lowerMessage.contains("connection refused")) {
            return true;
        }
        if (lowerMessage.contains("connection closed") || lowerMessage.contains("broken pipe")) {
            return true;
        }

        // Check the cause chain for timeout/connection errors
        Throwable cause = e.getCause();
        if (cause != null) {
            String causeName = cause.getClass().getName().toLowerCase();
            if (causeName.contains("timeout") || causeName.contains("connectexception")) {
                return true;
            }
        }

        // Try to extract HTTP status code from the message
        Integer statusCode = extractHttpStatus(message);
        if (statusCode != null) {
            // 429 (rate limit) is transient
            if (statusCode == 429) {
                return true;
            }
            // 5xx is transient
            if (statusCode >= 500 && statusCode < 600) {
                return true;
            }
            // 4xx (except 429) is non-transient
            if (statusCode >= 400 && statusCode < 500) {
                return false;
            }
        }

        // If we can't determine the type, treat as transient (prefer retry over immediate failure)
        return true;
    }

    /**
     * 从异常消息中提取 HTTP 状态码。
     *
     * @param message 异常消息
     * @return HTTP 状态码，未找到时返回 null
     */
    private Integer extractHttpStatus(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        Matcher matcher = HTTP_STATUS_PATTERN.matcher(message);
        if (matcher.find()) {
            String code = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            try {
                return Integer.parseInt(code);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 计算当前尝试的退避时间。
     * <p>
     * 公式: initialBackoffMs * backoffMultiplier^(attempt - 1)
     * <p>
     * attempt=1 表示第一次失败后的退避（乘数的 0 次方 = initialBackoffMs）。
     *
     * @param attempt 当前失败的尝试编号（从 1 开始）
     * @return 退避等待时间（毫秒）
     */
    long computeBackoff(int attempt) {
        return (long) (initialBackoffMs * Math.pow(backoffMultiplier, attempt - 1));
    }

    /**
     * 创建包含重试上下文信息的增强异常。
     *
     * @param original 原始异常
     * @param attempts 已执行的尝试次数
     * @return 包含尝试次数信息的 LlmException
     */
    private LlmException enriched(LlmException original, int attempts) {
        String enrichedMessage = String.format(
                "LLM call failed after %d attempt(s): %s",
                attempts,
                original.getMessage()
        );
        return new LlmException(enrichedMessage, original.getCause() != null ? original.getCause() : original);
    }

    /**
     * 虚拟线程安全的退避等待。
     * <p>
     * 在虚拟线程上调用 Thread.sleep() 是安全的——不会阻塞平台线程。
     *
     * @param millis 等待时间（毫秒）
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM retry interrupted", e);
        }
    }
}
