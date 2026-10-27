package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.dto.request.CreateAdminUserRequest;
import com.hotelbooking.booking.service.AdminUserService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.bind.annotation.DeleteMapping;
import java.time.Instant;
import java.util.Map;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserResponse> createUser(
            @Valid @RequestBody CreateAdminUserRequest request
    ) {
        AdminUserResponse response = adminUserService.createUser(request);

        return ResponseEntity
                .created(URI.create("/api/users/" + response.id()))
                .body(response);
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @PathVariable Long id
    ) {
        adminUserService.deleteUser(id);

        return ResponseEntity.ok(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 200,
                "code", "USER_DELETED",
                "message", "User deleted successfully",
                "path", "/api/users/" + id
        ));
    }

}