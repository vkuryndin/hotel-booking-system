package com.hotelbooking.booking.security;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.lang.reflect.Field;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LoginRegistrationRateLimiterCapacityTest {
    @Test void manyUniqueIdentitiesNeverGrowBeyondLimit() throws Exception {
        var limiter = new LoginRegistrationRateLimiter(
                Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC));
        for (int i = 0; i < 10_050; i++) {
            long retryAfter = limiter.acquire("ip:" + i, 5, Duration.ofMinutes(1));
            if (i < 10_000) assertEquals(0, retryAfter);
            else assertTrue(retryAfter > 0);
        }
        Field field = LoginRegistrationRateLimiter.class.getDeclaredField("counters");
        field.setAccessible(true);
        assertEquals(10_000, ((Map<?, ?>) field.get(limiter)).size());
        assertEquals(0, limiter.acquire("ip:0", 5, Duration.ofMinutes(1)));
    }

    @Test void differentWindowLengthsStillWork() {
        var limiter = new LoginRegistrationRateLimiter(
                Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC));
        assertEquals(0, limiter.acquire("registration", 1, Duration.ofHours(1)));
        assertTrue(limiter.acquire("registration", 1, Duration.ofHours(1)) > 0);
        assertEquals(0, limiter.acquire("login", 2, Duration.ofMinutes(1)));
    }
}
