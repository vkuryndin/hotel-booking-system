package com.hotelbooking.booking.dto.response;

import com.hotelbooking.booking.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
    @Schema(description = "Идентификатор пользователя.") Long id,
    @Schema(description = "Email пользователя.") String email,
    @Schema(description = "Имя пользователя.") String username,
    @Schema(description = "Телефон (если указан).") String phone,
    @Schema(description = "Имя.") String firstName,
    @Schema(description = "Фамилия.") String lastName,
    @Schema(description = "Роль пользователя.") Role role) {}
