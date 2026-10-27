package com.hotelbooking.booking.dto.response;

import com.hotelbooking.booking.entity.Role;

public record AdminUserResponse(
        Long id,
        String email,
        Role role
) {
}