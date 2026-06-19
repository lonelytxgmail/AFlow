package com.aflow.agent.executor;

/**
 * Truncates oversized tool results before adding them to the LLM message history.
 * <p>
 * Preserves the head (first 75% of max length) and tail (last 15% of max length),
 * inserting a marker in between indicating how many characters were removed.
 * This ensures the LLM retains the most relevant context from both the beginning
 * and end of tool outputs.
 *
 * @author AFlow Team
 * @since 1.0.0
 */
public class ToolResultTruncator {

    private final int maxLength;

    /**
     * Creates a new ToolResultTruncator with the specified maximum length threshold.
     *
     * @param maxLength the maximum allowed length for tool results (in characters)
     */
    public ToolResultTruncator(int maxLength) {
        this.maxLength = maxLength;
    }

    /**
     * Truncates the given tool result if it exceeds the configured maximum length.
     * <p>
     * If the result is null or within the allowed length, it is returned unchanged.
     * Otherwise, the head (75% of maxLength) and tail (15% of maxLength) are preserved,
     * with a marker indicating the number of removed characters inserted in between.
     *
     * @param toolResult the raw tool result string
     * @return the original string if within limits, or the truncated version with marker
     */
    public String truncate(String toolResult) {
        if (toolResult == null || toolResult.length() <= maxLength) {
            return toolResult;
        }
        int headLength = (int) (maxLength * 0.75);
        int tailLength = (int) (maxLength * 0.15);
        int removed = toolResult.length() - headLength - tailLength;
        return toolResult.substring(0, headLength)
                + "\n[...truncated " + removed + " characters...]\n"
                + toolResult.substring(toolResult.length() - tailLength);
    }
}
