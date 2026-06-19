package com.aflow.agent.executor;

import com.aflow.agent.llm.LlmMessage;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based test for token estimation formula in TokenBudgetManager.
 *
 * <p><b>Validates: Requirements 4.5</b></p>
 */
@Tag("Feature: engine-resilience, Property 6: Token estimation formula")
class TokenEstimationPropertyTest {

    private final TokenBudgetManager manager = new TokenBudgetManager(100_000);

    // CJK Unicode blocks used by the implementation
    private static final char[] CJK_SAMPLES = {
            '\u4E00',  // CJK Unified Ideographs start
            '\u9FFF',  // CJK Unified Ideographs end range
            '\u3400',  // CJK Extension A
            '\u3041',  // Hiragana
            '\u30A0',  // Katakana
            '\uAC00',  // Hangul Syllables
            '\u1100',  // Hangul Jamo
            '\u3100',  // Bopomofo
            '\u3300',  // CJK Compatibility Ideographs (CJK_COMPATIBILITY_IDEOGRAPHS is U+F900–U+FAFF)
            '\uF900',  // CJK Compatibility Ideographs
            '\u3000',  // CJK Symbols and Punctuation
    };

    /**
     * Property 6: Token estimation formula.
     * <p>
     * For random strings (length 0–10000) with mixed ASCII/CJK, verify estimation equals
     * {@code (nonCjkCount / 4) + (cjkCount / 2) + 4}.
     */
    @Property(tries = 20)
    void tokenEstimationMatchesFormula(@ForAll("mixedAsciiCjkStrings") String content) {
        LlmMessage msg = LlmMessage.user(content);

        int actual = manager.estimateMessageTokens(msg);

        int cjkCount = countCjk(content);
        int nonCjkCount = content.length() - cjkCount;
        int expected = (nonCjkCount / 4) + (cjkCount / 2) + 4;

        assert actual == expected :
                String.format("Expected %d but got %d for string of length %d (cjk=%d, nonCjk=%d)",
                        expected, actual, content.length(), cjkCount, nonCjkCount);
    }

    /**
     * Property 6 edge case: null/empty content returns message overhead (4).
     */
    @Property(tries = 20)
    void nullOrEmptyContentReturnsOverhead(@ForAll("emptyOrNullContent") String content) {
        LlmMessage msg = new LlmMessage("user", content);

        int actual = manager.estimateMessageTokens(msg);

        assert actual == 4 : "Expected 4 (message overhead) for null/empty content but got " + actual;
    }

    @Provide
    Arbitrary<String> mixedAsciiCjkStrings() {
        // Generate strings with a mix of ASCII and CJK characters, length 0–10000
        Arbitrary<Character> asciiChar = Arbitraries.chars().range('!', '~'); // printable ASCII
        Arbitrary<Character> cjkChar = Arbitraries.of(CJK_SAMPLES);

        // Frequency: roughly 60% ASCII, 40% CJK for good mixture
        Arbitrary<Character> mixedChar = Arbitraries.frequencyOf(
                Tuple.of(6, asciiChar),
                Tuple.of(4, cjkChar)
        );

        return mixedChar.list()
                .ofMinSize(0)
                .ofMaxSize(10_000)
                .map(chars -> {
                    StringBuilder sb = new StringBuilder(chars.size());
                    for (Character c : chars) {
                        sb.append(c);
                    }
                    return sb.toString();
                });
    }

    @Provide
    Arbitrary<String> emptyOrNullContent() {
        return Arbitraries.of("", null);
    }

    /**
     * Reference CJK detection matching the implementation's logic.
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

    private int countCjk(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isCjk(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }
}
