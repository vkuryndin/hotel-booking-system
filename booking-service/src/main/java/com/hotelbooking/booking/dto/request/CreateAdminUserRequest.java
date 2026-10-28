package com.hotelbooking.booking.dto.request;

import com.hotelbooking.booking.entity.Role;
import jakarta.validation.constraints.*;

public record CreateAdminUserRequest(

        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8)
        String password,

        @NotNull
        Role role,

        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{2,29}")
        String username,

        @Pattern(regexp = "^$|^\\+[1-9]\\d{7,14}$")
        String phone,

        @NotBlank @Size(max = 80)
        String firstName,

        @NotBlank @Size(max = 80)
        String lastName
) {
}