package com.hotelbooking.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        @Schema(description = "Время возникновения ошибки в UTC.") Instant timestamp,
        @Schema(description = "HTTP-код ответа.") int status,
        @Schema(description = "Машинно-читаемый код ошибки.") String code,
        @Schema(description = "Описание ошибки.") String message,
        @Schema(description = "Путь вызванного API.") String path,
        @Schema(description = "Идентификатор трассировки для диагностики.") String traceId,
        @Schema(description = "Ошибки валидации по именам полей (если есть).") Map<String, String> fieldErrors) {
    public ErrorResponse {
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("traceId");
        }
    }
}