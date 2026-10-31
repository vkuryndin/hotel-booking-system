package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.request.ChangePasswordRequest;
import com.hotelbooking.booking.service.UserPasswordService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;
import java.util.Map;

@Tag(name = "Профиль и пароль")
@RestController
@RequestMapping("/api/users")
public class UserPasswordController {

    private final UserPasswordService passwordService;

    public UserPasswordController(
            UserPasswordService passwordService
    ) {
        this.passwordService = passwordService;
    }

    @PostMapping("/me/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        passwordService.changePassword(userId, request);

        return ResponseEntity.ok(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 200,
                "code", "PASSWORD_CHANGED",
                "message", "Password changed successfully. Please log in again.",
                "path", "/api/users/me/change-password"
        ));
    }

    @GetMapping("/token-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> tokenStatus() {
        return ResponseEntity.noContent().build();
    }
}