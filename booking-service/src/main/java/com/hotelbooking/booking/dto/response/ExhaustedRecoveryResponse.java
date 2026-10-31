package com.hotelbooking.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import java.time.Instant;

public record ExhaustedRecoveryResponse(
        @Schema(description = "Идентификатор проблемного бронирования.") Long bookingId,
        @Schema(description = "Текущий статус бронирования.") BookingStatus status,
        @Schema(description = "Результат/этап создания бронирования.") BookingCreationOutcome creationOutcome,
        @Schema(description = "Количество выполненных попыток восстановления.") int recoveryAttempts,
        @Schema(description = "Запланированное время следующего восстановления (если установлено).") Instant nextRecoveryAt
) {
    public static ExhaustedRecoveryResponse from(Booking booking) {
        return new ExhaustedRecoveryResponse(
                booking.getId(), booking.getStatus(), booking.getCreationOutcome(),
                booking.getRecoveryAttempts(), booking.getNextRecoveryAt()
        );
    }
}
