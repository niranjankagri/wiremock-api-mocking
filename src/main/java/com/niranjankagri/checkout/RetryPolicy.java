package com.niranjankagri.checkout;

import java.time.Duration;
import java.util.Optional;

/**
 * How often to try, and how long to wait in between.
 *
 * @param maxAttempts the number of attempts, the first one included
 * @param firstDelay  the wait after the first attempt; it doubles after each further attempt
 * @param maxDelay    the longest wait, also when the provider's {@code Retry-After} asks for more
 */
public record RetryPolicy(int maxAttempts, Duration firstDelay, Duration maxDelay) {

    /**
     * The wait before the next attempt.
     *
     * @param attempt    the attempt that just failed, starting at 1
     * @param retryAfter the provider's {@code Retry-After} header, if any
     * @return the provider's wait in seconds if it gave one, otherwise the doubling delay; never above {@code maxDelay}
     */
    public Duration delayAfter(int attempt, Optional<String> retryAfter) {
        Duration wait = retryAfter
                // Retry-After can also be a date; that form falls back to the doubling delay
                .filter(value -> value.matches("\\d{1,9}"))
                .map(value -> Duration.ofSeconds(Long.parseLong(value)))
                .orElse(firstDelay.multipliedBy(1L << (attempt - 1)));
        return wait.compareTo(maxDelay) > 0 ? maxDelay : wait;
    }
}
