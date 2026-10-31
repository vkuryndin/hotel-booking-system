package com.hotelbooking.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ResetAdminPasswordResponse(
        @Schema(description = "Время выполнения операции.") Instant timestamp,
        @Schema(description = "HTTP-код результата.") int status,
        @Schema(description = "Код результата сброса пароля.") String code,
        @Schema(description = "Сообщение о результате.") String message,
        @Schema(description = "Путь операции сброса пароля.") String path
) {
}