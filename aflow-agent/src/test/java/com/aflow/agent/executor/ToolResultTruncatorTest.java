package com.aflow.agent.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link ToolResultTruncator}.
 *
 * Validates: Requirements 5.2, 5.3
 */
class ToolResultTruncatorTest {

    private static final int MAX_LENGTH = 100;
    private final ToolResultTruncator truncator = new ToolResultTruncator(MAX_LENGTH);

    @Test
    void truncate_nullInput_returnsNull() {
        assertNull(truncator.truncate(null));
    }

    @Test
    void truncate_emptyInput_returnsEmpty() {
        assertEquals("", truncator.truncate(""));
    }

    @Test
    void truncate_belowBoundary_returnsUnchanged() {
        String input = "a".repeat(50);
        assertEquals(input, truncator.truncate(input));
    }

    @Test
    void truncate_exactBoundary_returnsUnchanged() {
        String input = "b".repeat(MAX_LENGTH);
        assertEquals(input, truncator.truncate(input));
    }

    @Test
    void truncate_aboveBoundary_returnsCorrectHeadTailAndMarker() {
        // Create input longer than maxLength
        String input = buildInput(200);

        String result = truncator.truncate(input);

        int headLength = (int) (MAX_LENGTH * 0.75); // 75
        int tailLength = (int) (MAX_LENGTH * 0.15); // 15
        int removed = input.length() - headLength - tailLength; // 200 - 75 - 15 = 110

        // Verify head: first 75 characters of input
        String expectedHead = input.substring(0, headLength);
        assertEquals(expectedHead, result.substring(0, headLength));

        // Verify tail: last 15 characters of input
        String expectedTail = input.substring(input.length() - tailLength);
        assertEquals(expectedTail, result.substring(result.length() - tailLength));

        // Verify marker is present with correct character count
        String expectedMarker = "\n[...truncated " + removed + " characters...]\n";
        assertEquals(expectedHead + expectedMarker + expectedTail, result);
    }

    @Test
    void truncate_aboveBoundary_markerContainsCorrectRemovedCount() {
        // Use a different length to verify the formula generalizes
        ToolResultTruncator largeTruncator = new ToolResultTruncator(1000);
        String input = "x".repeat(5000);

        String result = largeTruncator.truncate(input);

        int headLength = (int) (1000 * 0.75); // 750
        int tailLength = (int) (1000 * 0.15); // 150
        int removed = 5000 - headLength - tailLength; // 5000 - 750 - 150 = 4100

        String marker = "[...truncated " + removed + " characters...]";
        assertEquals(true, result.contains(marker),
                "Expected marker '" + marker + "' not found in result");

        // Verify structure: head + \n + marker + \n + tail
        String expectedFull = input.substring(0, headLength)
                + "\n" + marker + "\n"
                + input.substring(input.length() - tailLength);
        assertEquals(expectedFull, result);
    }

    /**
     * Builds a deterministic input string of the given length using sequential characters
     * so that head and tail can be verified to be distinct portions.
     */
    private String buildInput(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        return sb.toString();
    }
}
