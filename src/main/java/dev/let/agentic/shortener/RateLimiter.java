package dev.let.agentic.shortener;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fixed-window rate limiter, per key (client address). In-memory, single node. */
public class RateLimiter {

    private record Window(long start, int count) { }

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        long now = clock.millis();
        Window w = windows.compute(key, (k, old) ->
                (old == null || now - old.start() >= windowMillis)
                        ? new Window(now, 1)
                        : new Window(old.start(), old.count() + 1));
        return w.count() <= limit;
    }
}
