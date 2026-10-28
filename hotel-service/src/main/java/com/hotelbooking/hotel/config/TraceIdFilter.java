package com.hotelbooking.hotel.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Trace-Id";

    private static final Logger log =
            LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String traceId = normalize(request.getHeader(HEADER));

        response.setHeader(HEADER, traceId);

        MDC.put("traceId", traceId);

        try {
            log.info(
                    "HTTP IN method={} path={}",
                    request.getMethod(),
                    request.getRequestURI()
            );

            filterChain.doFilter(request, response);

        } finally {
            log.info(
                    "HTTP OUT method={} path={} status={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus()
            );

            MDC.remove("traceId");
        }
    }

    private static String normalize(String supplied) {
        if (supplied != null) {
            try {
                UUID uuid = UUID.fromString(supplied);

                if (uuid.toString().equalsIgnoreCase(supplied)) {
                    return uuid.toString();
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid traceId: generate a new UUID.
            }
        }

        return UUID.randomUUID().toString();
    }
}