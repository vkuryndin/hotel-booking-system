package com.hotelbooking.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.booking.entity.Role;
import jakarta.validation.constraints.*;

public record CreateAdminUserRequest(

        @Schema(description = "Уникальный email пользователя.") @NotBlank @Email
        String email,

        @Schema(description = "Пароль новой учётной записи (не менее 8 символов).") @NotBlank @Size(min = 8)
        String password,

        @Schema(description = "Роль создаваемого пользователя: USER или ADMIN.") @NotNull
        Role role,

        @Schema(description = "Уникальное имя пользователя: 3–30 латинских символов, цифры, точка, дефис или подчёркивание; первый символ — буква.") @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{2,29}")
        String username,

        @Schema(description = "Телефон в международном формате +XXXXXXXX (необязательное поле).") @Pattern(regexp = "^$|^\\+[1-9]\\d{7,14}$")
        String phone,

        @Schema(description = "Имя пользователя, не более 80 символов.") @NotBlank @Size(max = 80)
        String firstName,

        @Schema(description = "Фамилия пользователя, не более 80 символов.") @NotBlank @Size(max = 80)
        String lastName
) {
}