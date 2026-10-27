package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.service.AdminUserReadService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class AdminUserReadController {

    private final AdminUserReadService readService;

    public AdminUserReadController(AdminUserReadService readService) {
        this.readService = readService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminUserResponse> getAllUsers() {
        return readService.getAllUsers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserResponse getUserById(@PathVariable Long id) {
        return readService.getUserById(id);
    }
}