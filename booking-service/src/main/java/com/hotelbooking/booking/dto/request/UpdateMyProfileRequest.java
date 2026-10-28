package com.hotelbooking.booking.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(

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