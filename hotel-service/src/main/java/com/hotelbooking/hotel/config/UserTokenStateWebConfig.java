
package com.hotelbooking.hotel.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Configuration
public class UserTokenStateWebConfig
        implements WebMvcConfigurer, HandlerInterceptor {

    private static final String GATEWAY_HEADER =
            "X-Gateway-Internal-Secret";

    private final byte[] gatewaySecret;

    public UserTokenStateWebConfig(
            @Value("${GATEWAY_INTERNAL_SECRET}") String secret
    ) {
        if (secret.isBlank()) {
            throw new IllegalArgumentException(
                    "GATEWAY_INTERNAL_SECRET must be set");
        }
        this.gatewaySecret =
                secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return true;
        }

        Jwt jwt = token.getToken();

        if ("SERVICE".equals(jwt.getClaimAsString("role"))) {
            if ("booking-service".equals(jwt.getSubject())
                    && jwt.getAudience().contains("hotel-service")) {
                return true;
            }
            return reject(request, response, 403,
                    "INVALID_SERVICE_TOKEN");
        }

        String supplied = request.getHeader(GATEWAY_HEADER);

        if (supplied != null && MessageDigest.isEqual(
                gatewaySecret,
                supplied.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }

        return reject(request, response, 403,
                "GATEWAY_REQUIRED");
    }

    private boolean reject(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(
                "{\"timestamp\":\"" + Instant.now()
                        + "\",\"status\":" + status
                        + ",\"code\":\"" + code + "\"}"
        );

        return false;
    }
}
