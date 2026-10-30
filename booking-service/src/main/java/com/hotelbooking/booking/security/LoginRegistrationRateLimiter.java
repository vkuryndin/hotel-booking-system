package com.hotelbooking.booking.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** In-memory fixed-window limits. Atomic per key and bounded by time-based eviction. */
@Component
public class LoginRegistrationRateLimiter {
    private record Bucket(long window, long expiresAt, AtomicInteger count) {}
    private final ConcurrentHashMap<String, Bucket> counters = new ConcurrentHashMap<>();
    private final Clock clock;
    private static final int MAX_KEYS = 10_000;
    private final ReentrantLock maintenance = new ReentrantLock();
    public LoginRegistrationRateLimiter() { this(Clock.systemUTC()); }
    LoginRegistrationRateLimiter(Clock clock) { this.clock = clock; }

    /** Returns seconds to retry; zero means request is accepted. */
    public long acquire(String key, int max, Duration window) {
        long seconds = window.getSeconds();
        long now = clock.instant().getEpochSecond();
        long slot = Math.floorDiv(now, seconds);
        // Guard key admission atomically so bursts of distinct IPs cannot grow the map without bound.
        maintenance.lock();
        try {
            if (!counters.containsKey(key) && counters.size() >= MAX_KEYS) {
                // Expire entries according to each bucket's own window size (not the incoming window).
                counters.entrySet().removeIf(e -> e.getValue().expiresAt <= now);
                if (counters.size() >= MAX_KEYS) {
                    // Fail closed for new identities; existing clients can still be checked.
                    return Math.max(1, seconds - Math.floorMod(now, seconds));
                }
            }
            AtomicInteger current = new AtomicInteger();
            counters.compute(key, (k, prev) -> {
                Bucket bucket = prev == null || prev.window != slot || prev.expiresAt <= now
                        ? new Bucket(slot, now - Math.floorMod(now, seconds) + seconds,
                                     new AtomicInteger()) : prev;
                current.set(bucket.count.incrementAndGet());
                return bucket;
            });
            return current.get() > max ? Math.max(1, seconds - Math.floorMod(now, seconds)) : 0;
        } finally {
            maintenance.unlock();
        }
    }
}
