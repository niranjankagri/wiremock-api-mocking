package com.niranjankagri.checkout.support;

import com.niranjankagri.checkout.Sleeper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Remembers the waits instead of waiting: retry tests stay fast, and the waits themselves can be asserted. */
public final class RecordingSleeper implements Sleeper {

    private final List<Duration> waits = new ArrayList<>();

    @Override
    public void sleep(Duration duration) {
        waits.add(duration);
    }

    /** @return every wait the client asked for, in order */
    public List<Duration> waits() {
        return List.copyOf(waits);
    }
}
