package com.aflow.agent.executor;

import com.aflow.agent.llm.LlmMessage;
import com.aflow.common.model.FlowContext;
import com.aflow.core.event.FlowEventBus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TokenBudgetManager}.
 * <p>
 * Validates: Requirements 4.2, 4.3, 4.4, 4.5
 */
class TokenBudgetManagerTest {

    // ─── No pruning when under budget ───────────────────────────────

    @Test
    void pruneIfNeeded_underBudget_returnsSameList() {
        // Budget is generous — messages should pass through unchanged
        TokenBudgetManager manager = new TokenBudgetManager(100_000);
        FlowEventBus eventBus = mock(FlowEventBus.class);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        List<LlmMessage> messages = List.of(
                LlmMessage.system("You are a helpful assistant."),
                LlmMessage.user("Hello"),
                LlmMessage.assistant("Hi there!")
        );

        List<LlmMessage> result = manager.pruneIfNeeded(messages, eventBus, ctx);

        assertSame(messages, result, "Should return the exact same list reference when under budget");
        verifyNoInteractions(eventBus);
    }

    @Test
    void pruneIfNeeded_exactlyAtBudget_returnsSameList() {
        // Calculate exact token count for the messages
        List<LlmMessage> messages = List.of(
                LlmMessage.system("test"),  // 4/4 + 4 = 5 tokens
                LlmMessage.user("hello")    // 5/4 + 4 = 5 tokens
        );

        TokenBudgetManager manager = new TokenBudgetManager(10);
        FlowEventBus eventBus = mock(FlowEventBus.class);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        // estimateTokens: "test" = 4 nonCJK/4 + 4 = 5; "hello" = 5/4 + 4 = 5; total = 10
        List<LlmMessage> result = manager.pruneIfNeeded(messages, eventBus, ctx);

        assertSame(messages, result, "Should return same list when exactly at budget");
        verifyNoInteractions(eventBus);
    }

    // ─── Placeholder insertion ──────────────────────────────────────

    @Test
    void pruneIfNeeded_overBudget_insertsPlaceholderAtIndex1() {
        // Use a very small budget to force pruning
        TokenBudgetManager manager = new TokenBudgetManager(30);
        FlowEventBus eventBus = mock(FlowEventBus.class);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system("System prompt for the agent"));
        messages.add(LlmMessage.user("First user message"));
        messages.add(LlmMessage.assistant("First assistant response that is somewhat long"));
        messages.add(LlmMessage.user("Second user question"));
        messages.add(LlmMessage.assistant("Second assistant response"));
        messages.add(LlmMessage.user("Third user question"));
        messages.add(LlmMessage.assistant("Third response"));

        List<LlmMessage> result = manager.pruneIfNeeded(messages, eventBus, ctx);

        // After pruning, index 1 should be the placeholder
        assertNotSame(messages, result);
        assertTrue(result.size() >= 2, "Result should have at least system + placeholder");
        assertEquals("system", result.get(1).getRole());
        assertEquals("[Earlier conversation removed due to token budget]", result.get(1).getContent());
    }

    @Test
    void pruneIfNeeded_overBudget_publishesPruneEvent() {
        TokenBudgetManager manager = new TokenBudgetManager(30);
        FlowEventBus eventBus = mock(FlowEventBus.class);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system("System prompt"));
        messages.add(LlmMessage.user("First question"));
        messages.add(LlmMessage.assistant("First answer that is long enough to exceed our tiny budget"));
        messages.add(LlmMessage.user("Second question"));
        messages.add(LlmMessage.assistant("Second answer"));
        messages.add(LlmMessage.user("Third question"));
        messages.add(LlmMessage.assistant("Third answer"));

        manager.pruneIfNeeded(messages, eventBus, ctx);

        verify(eventBus).publish(eq("inst-1"), eq("AGENT_TOKEN_PRUNE"), any());
    }

    // ─── Minimum preservation (system + last exchanges) ─────────────

    @Test
    void pruneIfNeeded_alwaysPreservesSystemMessage() {
        TokenBudgetManager manager = new TokenBudgetManager(20);
        FlowEventBus eventBus = mock(FlowEventBus.class);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system("Important system instructions"));
        messages.add(LlmMessage.user("Old message one"));
        messages.add(LlmMessage.assistant("Old response one that is really long to push us over budget"));
        messages.add(LlmMessage.user("Recent user question"));
        messages.add(LlmMessage.assistant("Recent response"));
        messages.add(LlmMessage.user("Most recent question"));
        messages.add(LlmMessage.assistant("Most recent answer"));

        List<LlmMessage> result = manager.pruneIfNeeded(messages, eventBus, ctx);

        // System message should always be at index 0
        assertEquals("system", result.get(0).getRole());
        assertEquals("Important system instructions", result.get(0).getContent());
    }

    @Test
    void pruneIfNeeded_preservesRecentExchangeRounds() {
        // Budget that will require pruning older messages but keep recent ones
        TokenBudgetManager manager = new TokenBudgetManager(50);
        FlowEventBus eventBus = mock(FlowEventBus.class);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system("sys"));
        // Old exchanges that can be pruned
        messages.add(LlmMessage.user("old user msg 1"));
        messages.add(LlmMessage.assistant("old assistant msg 1 that is very long to take up token budget space"));
        messages.add(LlmMessage.user("old user msg 2"));
        messages.add(LlmMessage.assistant("old assistant msg 2 also quite lengthy to consume tokens from our budget"));
        // Recent exchanges that should be preserved
        messages.add(LlmMessage.user("recent user msg 1"));
        messages.add(LlmMessage.assistant("recent assistant 1"));
        messages.add(LlmMessage.user("most recent user msg"));
        messages.add(LlmMessage.assistant("most recent answer"));

        List<LlmMessage> result = manager.pruneIfNeeded(messages, eventBus, ctx);

        // The most recent user message should always be in the result
        boolean hasRecentUser = result.stream()
                .anyMatch(m -> "most recent user msg".equals(m.getContent()));
        assertTrue(hasRecentUser, "Most recent user message should be preserved");

        // The most recent answer should also be in the result
        boolean hasRecentAssistant = result.stream()
                .anyMatch(m -> "most recent answer".equals(m.getContent()));
        assertTrue(hasRecentAssistant, "Most recent assistant response should be preserved");
    }

    // ─── CJK estimation ────────────────────────────────────────────

    @Test
    void estimateMessageTokens_cjkOnly() {
        TokenBudgetManager manager = new TokenBudgetManager(100_000);

        // "你好世界" = 4 CJK chars → (0/4) + (4/2) + 4 = 6 tokens
        LlmMessage msg = LlmMessage.user("你好世界");
        int tokens = manager.estimateMessageTokens(msg);

        assertEquals(6, tokens, "4 CJK chars: (0/4) + (4/2) + 4 = 6");
    }

    @Test
    void estimateMessageTokens_asciiOnly() {
        TokenBudgetManager manager = new TokenBudgetManager(100_000);

        // "hello world" = 11 non-CJK → (11/4) + (0/2) + 4 = 2 + 4 = 6 tokens (integer division)
        LlmMessage msg = LlmMessage.user("hello world");
        int tokens = manager.estimateMessageTokens(msg);

        assertEquals(6, tokens, "11 ASCII chars: (11/4) + (0/2) + 4 = 6");
    }

    @Test
    void estimateMessageTokens_mixedContent() {
        TokenBudgetManager manager = new TokenBudgetManager(100_000);

        // "hi你好" = 2 non-CJK + 2 CJK → (2/4) + (2/2) + 4 = 0 + 1 + 4 = 5
        LlmMessage msg = LlmMessage.user("hi你好");
        int tokens = manager.estimateMessageTokens(msg);

        assertEquals(5, tokens, "2 ASCII + 2 CJK: (2/4) + (2/2) + 4 = 5");
    }

    @Test
    void estimateMessageTokens_emptyContent() {
        TokenBudgetManager manager = new TokenBudgetManager(100_000);

        LlmMessage msg = LlmMessage.user("");
        int tokens = manager.estimateMessageTokens(msg);

        assertEquals(4, tokens, "Empty content should return just the message overhead (4)");
    }

    @Test
    void estimateMessageTokens_nullContent() {
        TokenBudgetManager manager = new TokenBudgetManager(100_000);

        LlmMessage msg = new LlmMessage("user", null);
        int tokens = manager.estimateMessageTokens(msg);

        assertEquals(4, tokens, "Null content should return just the message overhead (4)");
    }

    // ─── estimateTokens aggregation ─────────────────────────────────

    @Test
    void estimateTokens_sumsAllMessages() {
        TokenBudgetManager manager = new TokenBudgetManager(100_000);

        List<LlmMessage> messages = List.of(
                LlmMessage.system("test"),   // "test" = 4 chars → 4/4 + 4 = 5
                LlmMessage.user("hello")     // "hello" = 5 chars → 5/4 + 4 = 5
        );

        int total = manager.estimateTokens(messages);

        assertEquals(10, total, "Sum of individual estimates: 5 + 5 = 10");
    }
}
