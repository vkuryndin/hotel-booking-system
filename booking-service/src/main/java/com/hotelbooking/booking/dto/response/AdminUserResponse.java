package com.hotelbooking.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;

public record AdminUserResponse(
        @Schema(description = "Идентификатор пользователя.") Long id,
        @Schema(description = "Email пользователя.") String email,
        @Schema(description = "Имя пользователя.") String username,
        @Schema(description = "Номер телефона (если указан).") String phone,
        @Schema(description = "Имя.") String firstName,
        @Schema(description = "Фамилия.") String lastName,
        @Schema(description = "Роль пользователя: USER или ADMIN.") Role role
) {
    public static AdminUserResponse from(AppUser user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole()
        );
    }
}