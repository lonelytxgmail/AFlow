package com.aflow.agent.executor;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for ToolResultTruncator.truncate().
 *
 * <p><b>Validates: Requirements 5.2, 5.3</b></p>
 */
@Tag("Feature: engine-resilience, Property 7: Tool result truncation structure")
class ToolResultTruncatorPropertyTest {

    /**
     * Property 7: Tool result truncation structure
     *
     * <p>For random strings (length 0–50000) and random maxLength (100–10000), verify:</p>
     * <ul>
     *   <li>Head/tail preservation: output starts with s[0..headLen] where headLen = (int)(maxLength * 0.75)</li>
     *   <li>Output ends with s[len - tailLen..len] where tailLen = (int)(maxLength * 0.15)</li>
     *   <li>Marker content: contains [...truncated N characters...] where N = len - headLen - tailLen</li>
     *   <li>Passthrough for short strings: if s.length() <= maxLength, output equals s</li>
     * </ul>
     *
     * <p><b>Validates: Requirements 5.2, 5.3</b></p>
     */
    @Property(tries = 20)
    void truncationPreservesHeadAndTailWithMarker(
            @ForAll("randomStrings") String input,
            @ForAll @IntRange(min = 100, max = 10000) int maxLength
    ) {
        ToolResultTruncator truncator = new ToolResultTruncator(maxLength);
        String result = truncator.truncate(input);

        if (input.length() <= maxLength) {
            // Passthrough: short strings are returned unchanged
            assertEquals(input, result,
                    String.format("String of length %d should pass through unchanged with maxLength=%d",
                            input.length(), maxLength));
        } else {
            int headLen = (int) (maxLength * 0.75);
            int tailLen = (int) (maxLength * 0.15);
            int removed = input.length() - headLen - tailLen;

            // Head preservation
            String expectedHead = input.substring(0, headLen);
            assertTrue(result.startsWith(expectedHead),
                    String.format("Result should start with first %d chars of input (maxLength=%d, inputLen=%d)",
                            headLen, maxLength, input.length()));

            // Tail preservation
            String expectedTail = input.substring(input.length() - tailLen);
            assertTrue(result.endsWith(expectedTail),
                    String.format("Result should end with last %d chars of input (maxLength=%d, inputLen=%d)",
                            tailLen, maxLength, input.length()));

            // Marker content
            String expectedMarker = "[...truncated " + removed + " characters...]";
            assertTrue(result.contains(expectedMarker),
                    String.format("Result should contain marker '%s' (maxLength=%d, inputLen=%d)",
                            expectedMarker, maxLength, input.length()));
        }
    }

    @Provide
    Arbitrary<String> randomStrings() {
        return Arbitraries.strings()
                .ofMinLength(0)
                .ofMaxLength(50000)
                .withCharRange('!', '~')  // printable ASCII to avoid edge cases with surrogates
                .withChars('a', 'b', 'c', '1', '2', '3', ' ');
    }
}
