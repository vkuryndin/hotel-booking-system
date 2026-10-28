package com.hotelbooking.booking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 64)
        String password,

        @NotBlank
        @Pattern(
                regexp = "[A-Za-z][A-Za-z0-9._-]{2,29}",
                message = "Username must be 3-30 characters"
        )
        String username,

        @Pattern(
                regexp = "^$|^\\+[1-9]\\d{7,14}$",
                message = "Phone must use international format"
        )
        String phone,

        @NotBlank
        @Size(max = 80)
        String firstName,

        @NotBlank
        @Size(max = 80)
        String lastName

) {
}