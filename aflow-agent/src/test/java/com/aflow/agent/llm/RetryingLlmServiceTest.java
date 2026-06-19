package com.aflow.agent.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RetryingLlmService}.
 *
 * <p>Tests cover: transient classification, disabled retry, successful retry after
 * transient failure, and exhaustion with enriched exception.</p>
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.3, 3.4</b></p>
 */
class RetryingLlmServiceTest {

    private final LlmService delegate = mock(LlmService.class);

    // ========== isTransient classification tests ==========

    @Nested
    @DisplayName("isTransient classification")
    class IsTransientClassification {

        private final RetryingLlmService service = new RetryingLlmService(delegate, 3, 1, 2.0);

        @Test
        @DisplayName("HTTP status 503 → transient")
        void httpStatus503IsTransient() {
            LlmException ex = new LlmException("HTTP status 503 - Service Unavailable");
            assertTrue(service.isTransient(ex));
        }

        @Test
        @DisplayName("HTTP status 500 → transient")
        void httpStatus500IsTransient() {
            LlmException ex = new LlmException("HTTP status 500 - Internal Server Error");
            assertTrue(service.isTransient(ex));
        }

        @Test
        @DisplayName("HTTP status 502 → transient")
        void httpStatus502IsTransient() {
            LlmException ex = new LlmException("HTTP status 502 - Bad Gateway");
            assertTrue(service.isTransient(ex));
        }

        @Test
        @DisplayName("HTTP status 429 → transient (rate limit)")
        void httpStatus429IsTransient() {
            LlmException ex = new LlmException("HTTP status 429 - Too Many Requests");
            assertTrue(service.isTransient(ex));
        }

        @Test
        @DisplayName("connection reset → transient")
        void connectionResetIsTransient() {
            LlmException ex = new LlmException("connection reset by peer");
            assertTrue(service.isTransient(ex));
        }

        @Test
        @DisplayName("timeout → transient")
        void timeoutIsTransient() {
            LlmException ex = new LlmException("Request timeout after 30000ms");
            assertTrue(service.isTransient(ex));
        }

        @Test
        @DisplayName("HTTP status 400 → NOT transient")
        void httpStatus400IsNotTransient() {
            LlmException ex = new LlmException("HTTP status 400 - Bad Request");
            assertFalse(service.isTransient(ex));
        }

        @Test
        @DisplayName("HTTP status 401 → NOT transient")
        void httpStatus401IsNotTransient() {
            LlmException ex = new LlmException("HTTP status 401 - Unauthorized");
            assertFalse(service.isTransient(ex));
        }

        @Test
        @DisplayName("HTTP status 403 → NOT transient")
        void httpStatus403IsNotTransient() {
            LlmException ex = new LlmException("HTTP status 403 - Forbidden");
            assertFalse(service.isTransient(ex));
        }

        @Test
        @DisplayName("HTTP status 422 → NOT transient")
        void httpStatus422IsNotTransient() {
            LlmException ex = new LlmException("HTTP status 422 - Unprocessable Entity");
            assertFalse(service.isTransient(ex));
        }
    }

    // ========== Disabled retry (maxAttempts=1) ==========

    @Nested
    @DisplayName("Disabled retry (maxAttempts=1)")
    class DisabledRetry {

        @Test
        @DisplayName("on transient failure, throws immediately after 1 call")
        void throwsImmediatelyWhenRetryDisabled() {
            RetryingLlmService service = new RetryingLlmService(delegate, 1, 1, 2.0);
            AtomicInteger callCount = new AtomicInteger(0);

            when(delegate.chat(anyList())).thenAnswer(invocation -> {
                callCount.incrementAndGet();
                throw new LlmException("HTTP status 503 - Service Unavailable");
            });

            List<LlmMessage> messages = List.of(LlmMessage.user("test"));
            LlmException thrown = assertThrows(LlmException.class, () -> service.chat(messages));

            assertEquals(1, callCount.get(), "Should only call delegate once when maxAttempts=1");
            assertTrue(thrown.getMessage().contains("1 attempt(s)"),
                    "Exception should contain attempt count, got: " + thrown.getMessage());
        }

        @Test
        @DisplayName("on transient failure with chatWithTools, throws immediately after 1 call")
        void throwsImmediatelyForChatWithToolsWhenRetryDisabled() {
            RetryingLlmService service = new RetryingLlmService(delegate, 1, 1, 2.0);
            AtomicInteger callCount = new AtomicInteger(0);

            when(delegate.chatWithTools(anyList())).thenAnswer(invocation -> {
                callCount.incrementAndGet();
                throw new LlmException("HTTP status 503 - Service Unavailable");
            });

            List<LlmMessage> messages = List.of(LlmMessage.user("test"));
            assertThrows(LlmException.class, () -> service.chatWithTools(messages));

            assertEquals(1, callCount.get(), "Should only call delegate once when maxAttempts=1");
        }
    }

    // ========== Successful retry after transient failure ==========

    @Nested
    @DisplayName("Successful retry after transient failure")
    class SuccessfulRetry {

        @Test
        @DisplayName("delegate fails once (transient), succeeds on 2nd call → returns result")
        void retriesAndSucceedsOnSecondAttempt() {
            RetryingLlmService service = new RetryingLlmService(delegate, 3, 1, 2.0);
            AtomicInteger callCount = new AtomicInteger(0);

            when(delegate.chat(anyList())).thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    throw new LlmException("HTTP status 503 - Service Unavailable");
                }
                return "success response";
            });

            List<LlmMessage> messages = List.of(LlmMessage.user("test"));
            String result = service.chat(messages);

            assertEquals("success response", result);
            assertEquals(2, callCount.get(), "Delegate should have been called exactly 2 times");
        }

        @Test
        @DisplayName("chatWithTools: delegate fails once (transient), succeeds on 2nd call")
        void retriesAndSucceedsOnSecondAttemptForChatWithTools() {
            RetryingLlmService service = new RetryingLlmService(delegate, 3, 1, 2.0);
            AtomicInteger callCount = new AtomicInteger(0);

            LlmResponse expectedResponse = new LlmResponse();
            expectedResponse.setContent("tool response");

            when(delegate.chatWithTools(anyList())).thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    throw new LlmException("HTTP status 429 - Too Many Requests");
                }
                return expectedResponse;
            });

            List<LlmMessage> messages = List.of(LlmMessage.user("test"));
            LlmResponse result = service.chatWithTools(messages);

            assertEquals(expectedResponse, result);
            assertEquals(2, callCount.get(), "Delegate should have been called exactly 2 times");
        }
    }

    // ========== Exhaustion throws enriched exception ==========

    @Nested
    @DisplayName("Exhaustion throws enriched exception")
    class Exhaustion {

        @Test
        @DisplayName("delegate always fails with transient error, maxAttempts=3 → throws enriched exception")
        void throwsEnrichedExceptionAfterExhaustion() {
            RetryingLlmService service = new RetryingLlmService(delegate, 3, 1, 2.0);
            AtomicInteger callCount = new AtomicInteger(0);

            when(delegate.chat(anyList())).thenAnswer(invocation -> {
                callCount.incrementAndGet();
                throw new LlmException("HTTP status 503 - Service Unavailable");
            });

            List<LlmMessage> messages = List.of(LlmMessage.user("test"));
            LlmException thrown = assertThrows(LlmException.class, () -> service.chat(messages));

            assertEquals(3, callCount.get(), "Should exhaust all 3 attempts");
            assertTrue(thrown.getMessage().contains("3 attempt(s)"),
                    "Exception message should contain '3 attempt(s)', got: " + thrown.getMessage());
            assertNotNull(thrown.getCause(), "Enriched exception should have original cause");
        }

        @Test
        @DisplayName("enriched exception preserves original cause message")
        void enrichedExceptionPreservesOriginalCause() {
            RetryingLlmService service = new RetryingLlmService(delegate, 2, 1, 2.0);

            RuntimeException rootCause = new RuntimeException("network failure");
            when(delegate.chat(anyList())).thenThrow(
                    new LlmException("HTTP status 503 - Service Unavailable", rootCause));

            List<LlmMessage> messages = List.of(LlmMessage.user("test"));
            LlmException thrown = assertThrows(LlmException.class, () -> service.chat(messages));

            assertTrue(thrown.getMessage().contains("2 attempt(s)"),
                    "Exception should mention attempt count, got: " + thrown.getMessage());
            assertTrue(thrown.getMessage().contains("503"),
                    "Exception should contain original error info, got: " + thrown.getMessage());
            assertEquals(rootCause, thrown.getCause(),
                    "Enriched exception should preserve the original root cause");
        }
    }
}
