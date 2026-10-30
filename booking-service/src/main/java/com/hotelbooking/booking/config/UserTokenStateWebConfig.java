
package com.hotelbooking.booking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.repository.AppUserRepository;
import org.slf4j.MDC;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Configuration
public class UserTokenStateWebConfig
        implements WebMvcConfigurer, HandlerInterceptor {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String GATEWAY_HEADER =
            "X-Gateway-Internal-Secret";

    private final AppUserRepository userRepository;
    private final byte[] gatewaySecret;

    public UserTokenStateWebConfig(
            AppUserRepository userRepository,
            @Value("${GATEWAY_INTERNAL_SECRET}") String secret
    ) {
        if (secret.isBlank()) {
            throw new IllegalArgumentException(
                    "GATEWAY_INTERNAL_SECRET must be set");
        }
        this.userRepository = userRepository;
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

        String supplied = request.getHeader(GATEWAY_HEADER);

        if (supplied == null || !MessageDigest.isEqual(
                gatewaySecret,
                supplied.getBytes(StandardCharsets.UTF_8))) {
            return reject(request, response, 403,
                    "GATEWAY_REQUIRED", "Use API Gateway");
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return true;
        }

        Jwt jwt = token.getToken();

        if ("SERVICE".equals(jwt.getClaimAsString("role"))) {
            return true;
        }

        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ex) {
            return reject(request, response, 401,
                    "INVALID_TOKEN", "Invalid token subject");
        }

        Optional<AppUser> found = userRepository.findById(userId);

        if (found.isEmpty()) {
            return reject(request, response, 401,
                    "INVALID_TOKEN", "User no longer exists");
        }

        AppUser user = found.get();

        if (!user.getTokenIdentity().equals(
                jwt.getClaimAsString("tokenIdentity"))) {
            return reject(request, response, 401,
                    "TOKEN_REVOKED",
                    "Token identity is no longer valid");
        }

        Object claim = jwt.getClaim("tokenVersion");

        if (!(claim instanceof Number number)
                || number.longValue() != user.getTokenVersion()) {
            return reject(request, response, 401,
                    "TOKEN_REVOKED", "Token has been revoked");
        }

        boolean changePasswordRequest =
                "POST".equals(request.getMethod())
                        && "/api/users/me/change-password"
                        .equals(request.getRequestURI());

        if (user.isPasswordChangeRequired()
                && !changePasswordRequest) {
            return reject(request, response, 403,
                    "PASSWORD_CHANGE_REQUIRED",
                    "Password change required");
        }

        return true;
    }

    private boolean reject(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("code", code);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            body.put("traceId", traceId);
        }
        response.getWriter().write(JSON.writeValueAsString(body));
        return false;
    }
}
