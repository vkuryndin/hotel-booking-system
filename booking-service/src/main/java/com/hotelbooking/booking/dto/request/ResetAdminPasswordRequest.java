package com.hotelbooking.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetAdminPasswordRequest(
        @Schema(description = "Новый пароль пользователя: 8–64 символа.") @NotBlank
        @Size(min = 8, max = 64)
        String newPassword
) {
}