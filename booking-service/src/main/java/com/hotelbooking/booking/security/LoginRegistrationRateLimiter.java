package com.hotelbooking.booking.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** In-memory fixed-window limits. Atomic per key and bounded by time-based eviction. */
@Component
public class LoginRegistrationRateLimiter {
    private record Bucket(long window, AtomicInteger count) {}
    private final ConcurrentHashMap<String, Bucket> counters = new ConcurrentHashMap<>();
    private final Clock clock;
    public LoginRegistrationRateLimiter() { this(Clock.systemUTC()); }
    LoginRegistrationRateLimiter(Clock clock) { this.clock = clock; }

    /** Returns seconds to retry; zero means request is accepted. */
    public long acquire(String key, int max, Duration window) {
        long seconds = window.getSeconds();
        long now = clock.instant().getEpochSecond();
        long slot = Math.floorDiv(now, seconds);
        AtomicInteger current = new AtomicInteger();
        counters.compute(key, (k, prev) -> {
            Bucket bucket = prev == null || prev.window != slot
                    ? new Bucket(slot, new AtomicInteger()) : prev;
            current.set(bucket.count.incrementAndGet());
            return bucket;
        });
        if (counters.size() > 10000) {
            counters.entrySet().removeIf(e -> e.getValue().window < slot - 1);
        }
        return current.get() > max ? Math.max(1, seconds - Math.floorMod(now, seconds)) : 0;
    }
}
