package com.hotelbooking.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetAdminPasswordRequest(
        @NotBlank
        @Size(min = 8, max = 64)
        String newPassword
) {
}