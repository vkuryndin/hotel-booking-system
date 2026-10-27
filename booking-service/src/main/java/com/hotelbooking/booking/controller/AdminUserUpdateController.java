package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.request.ResetAdminPasswordRequest;
import com.hotelbooking.booking.dto.request.UpdateAdminUserRequest;
import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.dto.response.ResetAdminPasswordResponse;
import com.hotelbooking.booking.service.AdminUserUpdateService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/users")
public class AdminUserUpdateController {

    private final AdminUserUpdateService updateService;

    public AdminUserUpdateController(
            AdminUserUpdateService updateService
    ) {
        this.updateService = updateService;
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserResponse updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAdminUserRequest request
    ) {
        return updateService.updateUser(id, request);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResetAdminPasswordResponse> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetAdminPasswordRequest request
    ) {
        updateService.resetPassword(id, request);

        return ResponseEntity.ok(
                new ResetAdminPasswordResponse(
                        Instant.now(),
                        200,
                        "PASSWORD_RESET",
                        "Password reset successfully",
                        "/api/users/" + id + "/reset-password"
                )
        );
    }
}