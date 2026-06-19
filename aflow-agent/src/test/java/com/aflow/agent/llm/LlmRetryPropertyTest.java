package com.aflow.agent.llm;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for LLM retry behavior in RetryingLlmService.
 *
 * <p><b>Validates: Requirements 3.2, 3.3, 3.4</b></p>
 */
class LlmRetryPropertyTest {

    /**
     * Property 3: LLM retry on transient failure.
     * <p>
     * For random {@code (maxAttempts ∈ [1, 10], backoffMs ∈ [100, 5000], multiplier ∈ [1.0, 4.0])}
     * and all-transient failures, verify exactly maxAttempts calls are made.
     * <p>
     * <b>Validates: Requirements 3.2, 3.4</b>
     */
    @Property(tries = 20)
    @Tag("Feature: engine-resilience, Property 3: LLM retry on transient failure")
    void retriesExactlyMaxAttemptsOnTransientFailure(
            @ForAll @IntRange(min = 1, max = 10) int maxAttempts,
            @ForAll @IntRange(min = 100, max = 5000) int backoffMs,
            @ForAll("backoffMultipliers") double multiplier
    ) {
        // Arrange: mock delegate that always throws a transient (5xx) error
        LlmService delegate = mock(LlmService.class);
        AtomicInteger callCount = new AtomicInteger(0);

        when(delegate.chatWithTools(anyList())).thenAnswer(invocation -> {
            callCount.incrementAndGet();
            throw new LlmException("HTTP status 503 - Service Unavailable");
        });

        // Use initialBackoffMs=1 to minimize test time regardless of the generated backoffMs
        RetryingLlmService retrying = new RetryingLlmService(delegate, maxAttempts, 1, multiplier);

        // Act: call and expect failure after all retries exhausted
        List<LlmMessage> messages = List.of(LlmMessage.user("test"));
        try {
            retrying.chatWithTools(messages);
            assert false : "Expected LlmException to be thrown";
        } catch (LlmException e) {
            // Expected — retries exhausted
        }

        // Assert: exactly maxAttempts calls were made
        assert callCount.get() == maxAttempts :
                String.format("Expected %d calls but got %d (backoffMs=%d, multiplier=%.1f)",
                        maxAttempts, callCount.get(), backoffMs, multiplier);
    }

    /**
     * Property 4: LLM no-retry on non-transient failure.
     * <p>
     * For random HTTP status codes from {400, 401, 403, 422}, verify exactly 1 call made
     * and exception propagated immediately regardless of maxAttempts config.
     * <p>
     * <b>Validates: Requirements 3.3</b>
     */
    @Property(tries = 20)
    @Tag("Feature: engine-resilience, Property 4: LLM no-retry on non-transient failure")
    void noRetryOnNonTransientFailure(
            @ForAll("nonTransientStatusCodes") int statusCode,
            @ForAll @IntRange(min = 2, max = 10) int maxAttempts
    ) {
        // Arrange: mock delegate that throws a non-transient (4xx) error
        LlmService delegate = mock(LlmService.class);
        AtomicInteger callCount = new AtomicInteger(0);

        when(delegate.chatWithTools(anyList())).thenAnswer(invocation -> {
            callCount.incrementAndGet();
            throw new LlmException("HTTP status " + statusCode + " - Client Error");
        });

        // Use random maxAttempts (always > 1) — should still only call once for non-transient
        RetryingLlmService retrying = new RetryingLlmService(delegate, maxAttempts, 1, 2.0);

        // Act: call and expect immediate failure
        List<LlmMessage> messages = List.of(LlmMessage.user("test"));
        LlmException caught = null;
        try {
            retrying.chatWithTools(messages);
            assert false : "Expected LlmException to be thrown";
        } catch (LlmException e) {
            caught = e;
        }

        // Assert: exactly 1 call was made (no retries)
        assert callCount.get() == 1 :
                String.format("Expected exactly 1 call for non-transient status %d but got %d (maxAttempts=%d)",
                        statusCode, callCount.get(), maxAttempts);

        // Assert: exception was propagated with status code info
        assert caught != null : "Expected LlmException to be caught";
        assert caught.getMessage().contains(String.valueOf(statusCode)) :
                String.format("Expected exception message to contain status code %d, got: %s",
                        statusCode, caught.getMessage());
    }

    @Provide
    Arbitrary<Double> backoffMultipliers() {
        return Arbitraries.doubles().between(1.0, 4.0);
    }

    @Provide
    Arbitrary<Integer> nonTransientStatusCodes() {
        return Arbitraries.of(400, 401, 403, 422);
    }
}
