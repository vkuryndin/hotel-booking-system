package com.hotelbooking.booking.dto.request;

import com.hotelbooking.booking.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateAdminUserRequest(
        @Email
        @Size(max = 255)
        String email,

        Role role
) {
}