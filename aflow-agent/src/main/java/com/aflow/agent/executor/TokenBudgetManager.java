package com.aflow.agent.executor;

import com.aflow.agent.llm.LlmMessage;
import com.aflow.common.model.FlowContext;
import com.aflow.core.event.FlowEventBus;
import com.aflow.core.event.FlowEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Token budget manager that estimates token usage and prunes message history
 * when the budget is exceeded.
 * <p>
 * Uses a character-based heuristic: 4 characters per token for non-CJK text,
 * 2 characters per token for CJK text, plus a fixed overhead per message.
 *
 * @author AFlow Team
 * @since 1.0.0
 */
public class TokenBudgetManager {

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetManager.class);

    /** Fixed token overhead per message (framing tokens). */
    private static final int MESSAGE_OVERHEAD = 4;

    /** Number of recent exchange rounds to preserve during pruning. */
    private static final int PRESERVE_RECENT_EXCHANGES = 2;

    private final int maxTokenBudget;

    /**
     * Creates a new TokenBudgetManager with the specified token budget.
     *
     * @param maxTokenBudget the maximum estimated token count allowed for messages
     */
    public TokenBudgetManager(int maxTokenBudget) {
        this.maxTokenBudget = maxTokenBudget;
    }

    /**
     * Estimates the total token count for a list of messages.
     *
     * @param messages the messages to estimate
     * @return the estimated total token count
     */
    public int estimateTokens(List<LlmMessage> messages) {
        return messages.stream().mapToInt(this::estimateMessageTokens).sum();
    }

    /**
     * Prunes messages if the estimated token count exceeds the budget.
     * <p>
     * Preserves the system message (index 0) and the most recent N exchange rounds.
     * Removes the oldest tool-result and assistant messages first.
     * Inserts a summary placeholder at index 1 when pruning occurs.
     * Publishes an AGENT_TOKEN_PRUNE event with details.
     *
     * @param messages the current message list
     * @param eventBus the event bus for publishing prune events
     * @param ctx      the flow context providing the flow instance ID
     * @return the (possibly pruned) message list
     */
    public List<LlmMessage> pruneIfNeeded(List<LlmMessage> messages, FlowEventBus eventBus, FlowContext ctx) {
        int estimated = estimateTokens(messages);
        if (estimated <= maxTokenBudget) {
            return messages;
        }

        List<LlmMessage> pruned = doPrune(messages);
        pruned.add(1, LlmMessage.system("[Earlier conversation removed due to token budget]"));

        int estimatedAfter = estimateTokens(pruned);
        int messagesRemoved = messages.size() - pruned.size() + 1; // +1 for inserted placeholder

        publishPruneEvent(eventBus, ctx, messages.size() - (pruned.size() - 1), estimated, estimatedAfter);

        log.debug("Token budget pruning: before={}tokens/{}msgs, after={}tokens/{}msgs, removed={} messages",
                estimated, messages.size(), estimatedAfter, pruned.size(), messages.size() - (pruned.size() - 1));

        return pruned;
    }

    /**
     * Estimates the token count for a single message.
     * <p>
     * Formula: (nonCjkCharCount / 4) + (cjkCharCount / 2) + MESSAGE_OVERHEAD
     *
     * @param msg the message to estimate
     * @return the estimated token count
     */
    int estimateMessageTokens(LlmMessage msg) {
        String content = msg.getContent();
        if (content == null || content.isEmpty()) {
            return MESSAGE_OVERHEAD;
        }
        int cjkChars = countCjk(content);
        int nonCjk = content.length() - cjkChars;
        return (nonCjk / 4) + (cjkChars / 2) + MESSAGE_OVERHEAD;
    }

    /**
     * Performs the actual pruning logic.
     * <p>
     * Strategy:
     * - Always preserve the system message (index 0)
     * - Always preserve the most recent N exchange rounds (user + assistant/tool sequences)
     * - Remove oldest tool-result and assistant messages first from the middle section
     */
    private List<LlmMessage> doPrune(List<LlmMessage> messages) {
        if (messages.size() <= 2) {
            // Nothing meaningful to prune — return as-is
            return new ArrayList<>(messages);
        }

        // Find the boundary for recent exchanges to preserve
        int preserveFromIndex = findPreserveFromIndex(messages);

        // Build pruned list: system message + pruned middle + preserved tail
        List<LlmMessage> pruned = new ArrayList<>();
        pruned.add(messages.get(0)); // system message

        // Middle section: messages between system (index 0) and preserve boundary
        List<LlmMessage> middle = new ArrayList<>(messages.subList(1, preserveFromIndex));

        // Remove oldest tool and assistant messages from the middle until under budget
        // or the middle is exhausted
        while (!middle.isEmpty() && estimateTokens(buildCandidate(pruned, middle, messages, preserveFromIndex)) > maxTokenBudget) {
            // Remove the oldest removable message (tool or assistant)
            int removeIdx = findOldestRemovable(middle);
            if (removeIdx < 0) {
                // No more removable messages — remove oldest user message as last resort
                middle.remove(0);
            } else {
                middle.remove(removeIdx);
            }
        }

        pruned.addAll(middle);

        // Add preserved recent exchanges
        for (int i = preserveFromIndex; i < messages.size(); i++) {
            pruned.add(messages.get(i));
        }

        return pruned;
    }

    /**
     * Builds a candidate list to check estimated tokens during iterative pruning.
     */
    private List<LlmMessage> buildCandidate(List<LlmMessage> head, List<LlmMessage> middle,
                                             List<LlmMessage> original, int preserveFromIndex) {
        List<LlmMessage> candidate = new ArrayList<>(head);
        candidate.addAll(middle);
        // Add placeholder
        candidate.add(1, LlmMessage.system("[Earlier conversation removed due to token budget]"));
        for (int i = preserveFromIndex; i < original.size(); i++) {
            candidate.add(original.get(i));
        }
        return candidate;
    }

    /**
     * Finds the index from which to preserve recent exchanges (counting backwards).
     * Preserves the last PRESERVE_RECENT_EXCHANGES user-initiated exchange rounds.
     */
    private int findPreserveFromIndex(List<LlmMessage> messages) {
        int userCount = 0;
        int index = messages.size();

        for (int i = messages.size() - 1; i > 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                userCount++;
                if (userCount >= PRESERVE_RECENT_EXCHANGES) {
                    index = i;
                    break;
                }
            }
        }

        // If we couldn't find enough user messages, preserve from index 1
        // (which means preserve everything except system)
        if (userCount < PRESERVE_RECENT_EXCHANGES) {
            index = 1;
        }

        return index;
    }

    /**
     * Finds the oldest removable message in the middle section.
     * Prioritizes tool results and assistant messages.
     *
     * @return the index of the oldest removable message, or -1 if none found
     */
    private int findOldestRemovable(List<LlmMessage> middle) {
        // First pass: find oldest tool result
        for (int i = 0; i < middle.size(); i++) {
            if ("tool".equals(middle.get(i).getRole())) {
                return i;
            }
        }
        // Second pass: find oldest assistant message
        for (int i = 0; i < middle.size(); i++) {
            if ("assistant".equals(middle.get(i).getRole())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Counts the number of CJK characters in a string.
     * CJK characters are in the Unicode ranges for CJK Unified Ideographs and related blocks.
     */
    private int countCjk(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Determines if a character is in a CJK Unicode block.
     */
    private boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.BOPOMOFO;
    }

    /**
     * Publishes an AGENT_TOKEN_PRUNE event to the event bus.
     */
    private void publishPruneEvent(FlowEventBus eventBus, FlowContext ctx,
                                    int messagesRemoved, int tokensBefore, int tokensAfter) {
        try {
            eventBus.publish(ctx.getFlowInstanceId(), FlowEventType.AGENT_TOKEN_PRUNE.name(), Map.of(
                    "messagesRemoved", messagesRemoved,
                    "tokensBefore", tokensBefore,
                    "tokensAfter", tokensAfter
            ));
        } catch (Exception e) {
            log.debug("Failed to publish AGENT_TOKEN_PRUNE event", e);
        }
    }
}
