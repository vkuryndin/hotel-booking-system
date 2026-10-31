package com.hotelbooking.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(description = "JWT для заголовка Authorization: Bearer <токен>.") String accessToken,
        @Schema(description = "Тип выданного токена (Bearer).") String tokenType,
        @Schema(description = "Срок действия токена в секундах.") long expiresIn,
        @Schema(description = "Требуется сменить пароль.") boolean passwordChangeRequired
) {
}