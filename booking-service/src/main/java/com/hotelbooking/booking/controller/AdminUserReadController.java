package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.service.AdminUserReadService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

@RestController
@RequestMapping("/api/users")
public class AdminUserReadController {

    private final AdminUserReadService readService;

    public AdminUserReadController(AdminUserReadService readService) {
        this.readService = readService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AdminUserResponse> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return readService.getAllUsers(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserResponse getUserById(@PathVariable Long id) {
        return readService.getUserById(id);
    }
}