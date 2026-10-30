package com.hotelbooking.booking.security;

import com.hotelbooking.booking.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class PublicAuthRateLimitTest {
    private final LoginRegistrationRateLimiter limiter = new LoginRegistrationRateLimiter(
            Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC));
    private final PublicAuthRateLimitInterceptor interceptor = new PublicAuthRateLimitInterceptor(limiter, 2, 1);
    private MockHttpServletRequest request(String path, String ip, String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        return request;
    }
    private void check(MockHttpServletRequest request) {
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }
    @Test void loginThrottlesThirdAttemptWithRetryAfter() {
        var r = request("/api/users/login", "10.0.0.1", "POST");
        check(r); check(r);
        assertTrue(assertThrows(RateLimitExceededException.class, () -> check(r)).getRetryAfterSeconds() > 0);
    }
    @Test void registrationHasIndependentHourlyLimit() {
        var login = request("/api/users/login", "10.0.0.1", "POST");
        var reg = request("/api/users/register", "10.0.0.1", "POST");
        check(login); check(reg);
        assertThrows(RateLimitExceededException.class, () -> check(reg));
        check(login);
    }
    @Test void limitsArePerIp() {
        check(request("/api/users/register", "10.0.0.1", "POST"));
        check(request("/api/users/register", "10.0.0.2", "POST"));
    }
    @Test void cannotBypassLimitsUsingSpoofedForwardedHeader() {
        var r = request("/api/users/register", "10.0.0.1", "POST");
        r.addHeader("X-Forwarded-For", "1.2.3.4");
        check(r);
        r.removeHeader("X-Forwarded-For");
        r.addHeader("X-Forwarded-For", "5.6.7.8");
        assertThrows(RateLimitExceededException.class, () -> check(r));
    }
    @Test void otherMethodsAndRoutesRemainUnaffected() {
        for (int i=0; i<10; i++) {
            check(request("/api/users/login", "10.0.0.1", "GET"));
            check(request("/api/users/me", "10.0.0.1", "POST"));
        }
    }
}
