package com.hotelbooking.booking.dto.response;

import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;

public record AdminUserResponse(
        Long id,
        String email,
        String username,
        String phone,
        String firstName,
        String lastName,
        Role role
) {
    public static AdminUserResponse from(AppUser user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole()
        );
    }
}