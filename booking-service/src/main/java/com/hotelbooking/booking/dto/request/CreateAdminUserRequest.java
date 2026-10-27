package com.hotelbooking.booking.dto.request;

import com.hotelbooking.booking.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAdminUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotNull Role role
) {
}