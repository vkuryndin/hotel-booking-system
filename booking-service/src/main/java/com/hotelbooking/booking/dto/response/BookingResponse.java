package com.hotelbooking.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.booking.entity.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        @Schema(description = "Идентификатор бронирования.") Long id,
        @Schema(description = "Идентификатор владельца бронирования.") Long userId,
        @Schema(description = "Идентификатор выбранного номера.") Long roomId,
        @Schema(description = "Дата заезда, включительно.") LocalDate checkIn,
        @Schema(description = "Дата выезда, не включительно.") LocalDate checkOut,
        @Schema(description = "Текущий статус бронирования.") BookingStatus status,
        @Schema(description = "Время создания (UTC).") Instant createdAt,
        @Schema(description = "Количество гостей.") int guestCount
) {
}