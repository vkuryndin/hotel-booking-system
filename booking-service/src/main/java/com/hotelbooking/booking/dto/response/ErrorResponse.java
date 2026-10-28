package com.hotelbooking.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        Map<String, String> fieldErrors
) {
    public ErrorResponse {
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("traceId");
        }
    }
}