package com.hotelbooking.booking.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Separate per-account failed credential counter, independent of source IP. */
@Component
public class FailedAccountLoginLimiter {
    private record State(long window, int failures) {}
    private final ConcurrentHashMap<String, State> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxFailures;
    private final long windowSeconds;

    public FailedAccountLoginLimiter(@Value("${app.rate-limit.failed-login-per-account:5}") int maxFailures,
                                     @Value("${app.rate-limit.failed-login-window-seconds:900}") long windowSeconds) {
        this(Clock.systemUTC(), maxFailures, windowSeconds);
    }

    FailedAccountLoginLimiter(Clock clock, int maxFailures, long windowSeconds) {
        if (maxFailures < 1 || windowSeconds < 1) throw new IllegalArgumentException("Invalid login rate limit");
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.windowSeconds = windowSeconds;
    }

    public long retryAfter(String email) {
        long now = clock.instant().getEpochSecond();
        State state = attempts.get(email);
        if (state == null) return 0;
        if (now - state.window >= windowSeconds) {
            attempts.remove(email, state);
            return 0;
        }
        return state.failures >= maxFailures ? Math.max(1, windowSeconds - (now - state.window)) : 0;
    }

    public void recordFailure(String email) {
        long now = clock.instant().getEpochSecond();
        attempts.compute(email, (key, old) -> old == null || now - old.window >= windowSeconds
                ? new State(now, 1) : new State(old.window, old.failures + 1));
        if (attempts.size() > 10_000) {
            attempts.entrySet().removeIf(e -> now - e.getValue().window >= windowSeconds);
        }
    }

    public void clear(String email) { attempts.remove(email); }
}
