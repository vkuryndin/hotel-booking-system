package com.hotelbooking.booking.security;

import com.hotelbooking.booking.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PublicAuthRateLimitInterceptor implements HandlerInterceptor {
    private final LoginRegistrationRateLimiter limiter;
    private final int loginLimit;
    private final int registerLimit;
    public PublicAuthRateLimitInterceptor(LoginRegistrationRateLimiter limiter,
            @Value("${app.rate-limit.login-per-minute:20}") int loginLimit,
            @Value("${app.rate-limit.registration-per-hour:5}") int registerLimit) {
        this.limiter = limiter;
        this.loginLimit = loginLimit;
        this.registerLimit = registerLimit;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        boolean login = "/api/users/login".equals(path);
        boolean register = "/api/users/register".equals(path);
        if (!login && !register) return true;
        // Only the actual TCP peer is trusted: forwarding headers may be supplied by clients.
        String ip = request.getRemoteAddr();
        long retry = limiter.acquire((login ? "login:" : "register:") + ip,
                login ? loginLimit : registerLimit,
                login ? Duration.ofMinutes(1) : Duration.ofHours(1));
        if (retry > 0) throw new RateLimitExceededException(retry);
        return true;
    }
}
