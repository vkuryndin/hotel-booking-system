package com.hotelbooking.hotel.config;

import com.hotelbooking.hotel.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class SecurityExceptionHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public SecurityExceptionHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {

        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        writeError(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication required"
        );
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {

        writeError(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Insufficient permissions"
        );
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse error = new ErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                null,
                null
        );

        jsonMapper.writeValue(response.getOutputStream(), error);
    }
}