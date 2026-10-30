package com.hotelbooking.booking.security;

import com.hotelbooking.booking.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Limits sensitive authenticated mutations, reusing the S1-S2 limiter and 429 handler. */
@Component
public class SensitiveActionRateLimitInterceptor implements HandlerInterceptor {
    private final LoginRegistrationRateLimiter limiter;
    private final int passwordChangesPerHour;
    private final int adminPasswordResetsPerHour;
    private final int adminUserUpdatesPerMinute;

    public SensitiveActionRateLimitInterceptor(
            LoginRegistrationRateLimiter limiter,
            @Value("${app.rate-limit.password-change-per-hour:10}") int passwordChangesPerHour,
            @Value("${app.rate-limit.admin-password-reset-per-hour:10}") int adminPasswordResetsPerHour,
            @Value("${app.rate-limit.admin-user-update-per-minute:30}") int adminUserUpdatesPerMinute) {
        this.limiter = limiter;
        this.passwordChangesPerHour = passwordChangesPerHour;
        this.adminPasswordResetsPerHour = adminPasswordResetsPerHour;
        this.adminUserUpdatesPerMinute = adminUserUpdatesPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String operation;
        int max;
        Duration window;
        if ("POST".equalsIgnoreCase(method) && "/api/users/me/change-password".equals(path)) {
            operation = "password-change";
            max = passwordChangesPerHour;
            window = Duration.ofHours(1);
        } else if ("POST".equalsIgnoreCase(method) && path.matches("/api/users/[0-9]+/reset-password")) {
            operation = "admin-password-reset";
            max = adminPasswordResetsPerHour;
            window = Duration.ofHours(1);
        } else if ("PATCH".equalsIgnoreCase(method) && path.matches("/api/users/[0-9]+")) {
            operation = "admin-user-update";
            max = adminUserUpdatesPerMinute;
            window = Duration.ofMinutes(1);
        } else {
            return true;
        }

        // Spring Security authentication is already populated before MVC interceptors.
        // Do not trust unverified identity in request headers.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return true; // The existing security filter will reject unauthenticated requests.
        }
        String identity = auth.getName();
        long retry = limiter.acquire("sensitive:" + operation + ":" + identity, max, window);
        if (retry > 0) throw new RateLimitExceededException(retry);
        return true;
    }
}
