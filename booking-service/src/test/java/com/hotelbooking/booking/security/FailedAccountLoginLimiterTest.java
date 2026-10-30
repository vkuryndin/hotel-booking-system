package com.hotelbooking.booking.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FailedAccountLoginLimiterTest {
    @Test void fifthFailureBlocksSameAccountButNotOtherAccount() {
        var limiter = new FailedAccountLoginLimiter(Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC), 5, 900);
        for (int i=0; i<5; i++) limiter.recordFailure("a@test.example");
        assertTrue(limiter.retryAfter("a@test.example") > 0);
        assertEquals(0, limiter.retryAfter("b@test.example"));
        limiter.clear("a@test.example");
        assertEquals(0, limiter.retryAfter("a@test.example"));
    }
    @Test void expiredWindowIsUnlocked() {
        var time = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-30T10:00:00Z"));
        Clock clock = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return time.get(); }
        };
        var limiter = new FailedAccountLoginLimiter(clock, 2, 900);
        limiter.recordFailure("x"); limiter.recordFailure("x");
        assertTrue(limiter.retryAfter("x") > 0);
        time.set(time.get().plusSeconds(900));
        assertEquals(0, limiter.retryAfter("x"));
    }
}
