package com.aflow.common.model;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based test for RetryPolicy.computeBackoff().
 *
 * <p><b>Validates: Requirements 6.3</b></p>
 */
@Tag("Feature: engine-resilience, Property 9: Backoff duration calculation")
class BackoffCalculationPropertyTest {

    /**
     * Property 9: Backoff duration calculation
     *
     * <p>For any (backoffMs, backoffMultiplier, attemptNumber) where attemptNumber >= 1,
     * RetryPolicy.computeBackoff(attemptNumber) SHALL return
     * floor(backoffMs * backoffMultiplier^(attemptNumber - 1)).</p>
     *
     * <p><b>Validates: Requirements 6.3</b></p>
     */
    @Property(tries = 20)
    void backoffDurationMatchesFormula(
            @ForAll @LongRange(min = 100, max = 10000) long backoffMs,
            @ForAll @DoubleRange(min = 1.0, max = 5.0) double multiplier,
            @ForAll @IntRange(min = 1, max = 10) int attempt
    ) {
        RetryPolicy policy = new RetryPolicy(3, backoffMs, multiplier);

        long actual = policy.computeBackoff(attempt);
        long expected = (long) (backoffMs * Math.pow(multiplier, attempt - 1));

        assertEquals(expected, actual,
                String.format("computeBackoff(%d) with backoffMs=%d, multiplier=%.2f should be %d but was %d",
                        attempt, backoffMs, multiplier, expected, actual));
    }
}
