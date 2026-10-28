package com.hotelbooking.booking.dto.response;

import com.hotelbooking.booking.entity.Role;

public record UserResponse(
        Long id,
        String email,
        String username,
        String phone,
        String firstName,
        String lastName,
        Role role
) {
}