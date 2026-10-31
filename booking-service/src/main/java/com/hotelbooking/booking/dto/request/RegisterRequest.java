package com.hotelbooking.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @Schema(description = "Уникальный email, до 255 символов.") @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255)
        String email,

        @Schema(description = "Пароль от 8 до 64 символов.") @NotBlank(message = "Password is required")
        @Size(min = 8, max = 64)
        String password,

        @Schema(description = "Уникальное имя пользователя, 3–30 допустимых символов.") @NotBlank
        @Pattern(
                regexp = "[A-Za-z][A-Za-z0-9._-]{2,29}",
                message = "Username must be 3-30 characters"
        )
        String username,

        @Schema(description = "Необязательный телефон в международном формате, начиная с +.") @Pattern(
                regexp = "^$|^\\+[1-9]\\d{7,14}$",
                message = "Phone must use international format"
        )
        String phone,

        @Schema(description = "Имя, до 80 символов.") @NotBlank
        @Size(max = 80)
        String firstName,

        @Schema(description = "Фамилия, до 80 символов.") @NotBlank
        @Size(max = 80)
        String lastName

) {
}