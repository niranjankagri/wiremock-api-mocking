package com.niranjankagri.checkout;

import java.time.Duration;

/** Waits between attempts. An interface so the tests can record the waits instead of sitting through them. */
@FunctionalInterface
public interface Sleeper {

    /**
     * Waits.
     *
     * @param duration how long
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    void sleep(Duration duration) throws InterruptedException;
}
