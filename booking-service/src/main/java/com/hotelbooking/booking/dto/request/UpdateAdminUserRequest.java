package com.hotelbooking.booking.dto.request;

import com.hotelbooking.booking.entity.Role;
import jakarta.validation.constraints.*;

public record UpdateAdminUserRequest(

        @Email
        @Size(max = 255)
        String email,

        Role role,

        @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{2,29}")
        String username,

        @Pattern(regexp = "^$|^\\+[1-9]\\d{7,14}$")
        String phone,

        @Size(max = 80)
        @Pattern(regexp = ".*\\S.*")
        String firstName,

        @Size(max = 80)
        @Pattern(regexp = ".*\\S.*")
        String lastName
) {
}