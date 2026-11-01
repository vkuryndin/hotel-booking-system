package com.hotelbooking.booking.dto.request;

import com.hotelbooking.booking.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAdminUserRequest(
    @Schema(description = "Новый email (если изменяется).") @Email @Size(max = 255) String email,
    @Schema(description = "Новая роль USER или ADMIN (с учётом административных ограничений).")
        Role role,
    @Schema(description = "Новое имя пользователя.")
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{2,29}")
        String username,
    @Schema(description = "Новый телефон в международном формате.")
        @Pattern(regexp = "^$|^\\+[1-9]\\d{7,14}$")
        String phone,
    @Schema(description = "Новое имя.") @Size(max = 80) @Pattern(regexp = ".*\\S.*")
        String firstName,
    @Schema(description = "Новая фамилия.") @Size(max = 80) @Pattern(regexp = ".*\\S.*")
        String lastName) {}
