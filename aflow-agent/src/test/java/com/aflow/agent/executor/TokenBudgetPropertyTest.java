package com.aflow.agent.executor;

import com.aflow.agent.llm.LlmMessage;
import com.aflow.common.model.FlowContext;
import com.aflow.core.event.FlowEventBus;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Property-based test for token budget pruning invariant in TokenBudgetManager.
 *
 * <p><b>Validates: Requirements 4.2, 4.3, 4.4</b></p>
 */
@Tag("Feature: engine-resilience, Property 5: Token budget pruning invariant")
class TokenBudgetPropertyTest {

    private static final String[] ROLES = {"user", "assistant", "tool"};

    /**
     * Property 5: Token budget pruning invariant.
     *
     * <p>For random message lists (1–50 messages) and random budgets [50, 10000], verify:</p>
     * <ul>
     *   <li>Post-prune estimate &lt;= budget (unless minimum set already exceeds budget)</li>
     *   <li>System message preserved (index 0)</li>
     *   <li>Most recent user message content exists in result</li>
     * </ul>
     *
     * <p><b>Validates: Requirements 4.2, 4.3, 4.4</b></p>
     */
    @Property(tries = 20)
    void pruningPreservesInvariantsAcrossRandomInputs(
            @ForAll("randomMessageLists") List<LlmMessage> messages,
            @ForAll @IntRange(min = 50, max = 10000) int budget
    ) {
        TokenBudgetManager manager = new TokenBudgetManager(budget);
        FlowEventBus eventBus = mock(FlowEventBus.class);
        FlowContext ctx = new FlowContext("test-instance", "test-def");

        // Make a mutable copy since pruneIfNeeded may need a mutable list
        List<LlmMessage> input = new ArrayList<>(messages);

        List<LlmMessage> result = manager.pruneIfNeeded(input, eventBus, ctx);

        // --- Invariant 1: Post-prune estimate <= budget ---
        // Edge case: if the minimum set (system + recent exchange) already exceeds budget,
        // pruning does its best but can't violate minimum preservation
        int postPruneTokens = manager.estimateTokens(result);
        int systemTokens = manager.estimateMessageTokens(messages.get(0));

        // Find the most recent user message to calculate minimum set tokens
        String mostRecentUserContent = findMostRecentUserContent(messages);
        int minimumSetTokens = calculateMinimumSetTokens(manager, messages);

        if (minimumSetTokens <= budget) {
            // Normal case: pruning should bring us within budget
            assert postPruneTokens <= budget :
                    String.format("Post-prune tokens (%d) should be <= budget (%d). " +
                                    "Input had %d messages, result has %d messages.",
                            postPruneTokens, budget, messages.size(), result.size());
        }
        // If minimum set exceeds budget, pruning preserves the minimum set regardless — test still passes

        // --- Invariant 2: System message preserved at index 0 ---
        assert "system".equals(result.get(0).getRole()) :
                String.format("First message role should be 'system' but was '%s'", result.get(0).getRole());
        assert messages.get(0).getContent().equals(result.get(0).getContent()) :
                "System message content should be preserved";

        // --- Invariant 3: Most recent user message content exists in result ---
        if (mostRecentUserContent != null) {
            boolean recentUserPreserved = result.stream()
                    .anyMatch(m -> mostRecentUserContent.equals(m.getContent()));
            assert recentUserPreserved :
                    String.format("Most recent user message '%s' should be preserved in result. " +
                                    "Result has %d messages.",
                            truncateForDisplay(mostRecentUserContent), result.size());
        }
    }

    @Provide
    Arbitrary<List<LlmMessage>> randomMessageLists() {
        // Generate message lists where index 0 is always system, subsequent messages alternate roles
        Arbitrary<String> contentArbitrary = Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(500)
                .withCharRange('a', 'z')
                .withChars(' ', '.', ',', '!');

        Arbitrary<String> systemContent = Arbitraries.strings()
                .ofMinLength(10)
                .ofMaxLength(200)
                .withCharRange('a', 'z')
                .withChars(' ', '.');

        // Generate between 2 and 50 follow-up messages (so total is 3-51, first is system)
        return Combinators.combine(systemContent, contentArbitrary.list().ofMinSize(2).ofMaxSize(49))
                .as((sysContent, contents) -> {
                    List<LlmMessage> msgs = new ArrayList<>();
                    msgs.add(LlmMessage.system(sysContent));

                    // Alternate user/assistant/tool roles for subsequent messages
                    for (int i = 0; i < contents.size(); i++) {
                        String role;
                        int mod = i % 3;
                        if (mod == 0) {
                            role = "user";
                        } else if (mod == 1) {
                            role = "assistant";
                        } else {
                            role = "tool";
                        }
                        LlmMessage msg = new LlmMessage(role, contents.get(i));
                        if ("tool".equals(role)) {
                            msg.setToolCallId("tc-" + i);
                        }
                        msgs.add(msg);
                    }

                    // Ensure the list ends with at least one user message for invariant 3
                    // by adding a final user message if the last message isn't one
                    if (!"user".equals(msgs.get(msgs.size() - 1).getRole())) {
                        msgs.add(LlmMessage.user("final user question"));
                    }

                    return msgs;
                });
    }

    /**
     * Finds the content of the most recent user message in the list.
     */
    private String findMostRecentUserContent(List<LlmMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                return messages.get(i).getContent();
            }
        }
        return null;
    }

    /**
     * Estimates the minimum set of tokens that pruning will always preserve:
     * system message + the last 2 user-initiated exchange rounds.
     */
    private int calculateMinimumSetTokens(TokenBudgetManager manager, List<LlmMessage> messages) {
        int total = manager.estimateMessageTokens(messages.get(0)); // system message

        // Find the preserve boundary (same logic as the implementation)
        int userCount = 0;
        int preserveFromIndex = messages.size();
        for (int i = messages.size() - 1; i > 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                userCount++;
                if (userCount >= 2) {
                    preserveFromIndex = i;
                    break;
                }
            }
        }
        if (userCount < 2) {
            preserveFromIndex = 1;
        }

        // Add tokens for preserved tail
        for (int i = preserveFromIndex; i < messages.size(); i++) {
            total += manager.estimateMessageTokens(messages.get(i));
        }

        // Add placeholder token estimate
        total += manager.estimateMessageTokens(
                LlmMessage.system("[Earlier conversation removed due to token budget]"));

        return total;
    }

    /**
     * Truncates a string for display in assertion messages.
     */
    private String truncateForDisplay(String s) {
        if (s == null) return "null";
        if (s.length() <= 40) return s;
        return s.substring(0, 37) + "...";
    }
}
