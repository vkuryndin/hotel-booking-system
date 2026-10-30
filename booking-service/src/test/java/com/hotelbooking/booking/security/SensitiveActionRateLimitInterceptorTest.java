package com.hotelbooking.booking.security;

import com.hotelbooking.booking.exception.RateLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveActionRateLimitInterceptorTest {
    private final SensitiveActionRateLimitInterceptor interceptor =
            new SensitiveActionRateLimitInterceptor(new LoginRegistrationRateLimiter(), 2, 2, 2);

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        return req;
    }

    private void authenticate(String user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "n/a", java.util.List.of()));
    }

    @Test
    void passwordChangeIsLimitedPerAuthenticatedUser() {
        authenticate("user-1");
        var req = request("POST", "/api/users/me/change-password");
        var resp = new MockHttpServletResponse();
        assertDoesNotThrow(() -> interceptor.preHandle(req, resp, new Object()));
        assertDoesNotThrow(() -> interceptor.preHandle(req, resp, new Object()));
        assertThrows(RateLimitExceededException.class,
                () -> interceptor.preHandle(req, resp, new Object()));
        authenticate("user-2");
        assertDoesNotThrow(() -> interceptor.preHandle(req, resp, new Object()));
    }

    @Test
    void adminResetAndUpdateUseIndependentLimits() {
        authenticate("admin-1");
        var resp = new MockHttpServletResponse();
        var reset = request("POST", "/api/users/42/reset-password");
        var update = request("PATCH", "/api/users/42");
        assertDoesNotThrow(() -> interceptor.preHandle(reset, resp, new Object()));
        assertDoesNotThrow(() -> interceptor.preHandle(reset, resp, new Object()));
        assertThrows(RateLimitExceededException.class,
                () -> interceptor.preHandle(reset, resp, new Object()));
        assertDoesNotThrow(() -> interceptor.preHandle(update, resp, new Object()));
    }

    @Test
    void otherMethodsAreNotLimited() {
        authenticate("admin-1");
        var req = request("GET", "/api/users/42");
        for (int i = 0; i < 5; i++)
            assertDoesNotThrow(() -> interceptor.preHandle(req, new MockHttpServletResponse(), new Object()));
    }
}
