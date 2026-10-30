package com.hotelbooking.booking.config;

import com.hotelbooking.booking.security.PublicAuthRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PublicAuthRateLimitConfig implements WebMvcConfigurer {
    private final PublicAuthRateLimitInterceptor interceptor;
    public PublicAuthRateLimitConfig(PublicAuthRateLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/users/login", "/api/users/register");
    }
}
