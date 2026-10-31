package com.hotelbooking.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @Schema(description = "Действующий пароль.") @NotBlank String currentPassword,
    @Schema(description = "Новый пароль: от 8 до 64 символов.") @NotBlank @Size(min = 8, max = 64)
        String newPassword) {}
