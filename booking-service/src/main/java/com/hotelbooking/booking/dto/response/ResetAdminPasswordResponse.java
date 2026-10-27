package com.hotelbooking.booking.dto.response;

import java.time.Instant;

public record ResetAdminPasswordResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path
) {
}