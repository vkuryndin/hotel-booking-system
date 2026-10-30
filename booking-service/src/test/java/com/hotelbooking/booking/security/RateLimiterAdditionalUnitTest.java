package com.hotelbooking.booking.security;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RateLimiterAdditionalUnitTest {
    @Test void exactLimitAndNextAttemptRejected() {
        var limiter=new LoginRegistrationRateLimiter(Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"),ZoneOffset.UTC));
        for(int i=0;i<5;i++)assertEquals(0,limiter.acquire("account:one",5,Duration.ofMinutes(15)));
        assertTrue(limiter.acquire("account:one",5,Duration.ofMinutes(15))>0);
    }
    @Test void independentKeysHaveIndependentWindows() {
        var limiter=new LoginRegistrationRateLimiter(Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"),ZoneOffset.UTC));
        assertEquals(0,limiter.acquire("a",1,Duration.ofMinutes(1)));
        assertTrue(limiter.acquire("a",1,Duration.ofMinutes(1))>0);
        assertEquals(0,limiter.acquire("b",1,Duration.ofMinutes(1)));
    }
    @Test void concurrentCallsCannotAllBypassLimit() throws Exception {
        var limiter=new LoginRegistrationRateLimiter(Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"),ZoneOffset.UTC));
        ExecutorService p=Executors.newFixedThreadPool(20);
        try {
            var work=new java.util.ArrayList<Callable<Long>>();
            for(int i=0;i<200;i++)work.add(()->limiter.acquire("shared",5,Duration.ofMinutes(15)));
            var futures=p.invokeAll(work);
            int admitted=0;
            for(var f:futures)if(f.get()==0)admitted++;
            assertEquals(5,admitted);
        } finally {p.shutdownNow();}
    }
}
