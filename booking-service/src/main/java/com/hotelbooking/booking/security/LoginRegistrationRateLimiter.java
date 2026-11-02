package com.hotelbooking.booking.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/** In-memory fixed-window limits with bounded key storage. */
@Component
public class LoginRegistrationRateLimiter {
  private record Bucket(long window, long expiresAt, AtomicInteger count) {}

  private final ConcurrentHashMap<String, Bucket> counters = new ConcurrentHashMap<>();
  private final Clock clock;
  private static final int MAX_KEYS = 10_000;
  private final ReentrantLock maintenance = new ReentrantLock();

  public LoginRegistrationRateLimiter() {
    this(Clock.systemUTC());
  }

  LoginRegistrationRateLimiter(Clock clock) {
    this.clock = clock;
  }

  /** Returns seconds to retry; zero means request is accepted. */
  public long acquire(String key, int max, Duration window) {
    long seconds = window.getSeconds();
    long now = clock.instant().getEpochSecond();
    long slot = Math.floorDiv(now, seconds);
    // Serialize new-key admission to bound memory use under many distinct client IPs.
    maintenance.lock();
    try {
      if (!counters.containsKey(key) && counters.size() >= MAX_KEYS) {
        // Buckets may have different windows; expire each against its own deadline.
        counters.entrySet().removeIf(e -> e.getValue().expiresAt <= now);
        if (counters.size() >= MAX_KEYS) {
          // Reject new keys at capacity; existing buckets remain enforceable.
          return Math.max(1, seconds - Math.floorMod(now, seconds));
        }
      }
      AtomicInteger current = new AtomicInteger();
      counters.compute(
          key,
          (k, prev) -> {
            Bucket bucket =
                prev == null || prev.window != slot || prev.expiresAt <= now
                    ? new Bucket(
                        slot, now - Math.floorMod(now, seconds) + seconds, new AtomicInteger())
                    : prev;
            current.set(bucket.count.incrementAndGet());
            return bucket;
          });
      return current.get() > max ? Math.max(1, seconds - Math.floorMod(now, seconds)) : 0;
    } finally {
      maintenance.unlock();
    }
  }
}
