package com.hotelbooking.booking.config;

import com.hotelbooking.booking.security.PublicAuthRateLimitInterceptor;
import com.hotelbooking.booking.security.SensitiveActionRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PublicAuthRateLimitConfig implements WebMvcConfigurer {
    private final PublicAuthRateLimitInterceptor publicAuth;
    private final SensitiveActionRateLimitInterceptor sensitive;

    public PublicAuthRateLimitConfig(PublicAuthRateLimitInterceptor publicAuth,
                                     SensitiveActionRateLimitInterceptor sensitive) {
        this.publicAuth = publicAuth;
        this.sensitive = sensitive;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(publicAuth).addPathPatterns("/api/users/login", "/api/users/register");
        registry.addInterceptor(sensitive).addPathPatterns("/api/users/**");
    }
}
