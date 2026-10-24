package com.hotelbooking.booking.dto.response;

import com.hotelbooking.booking.entity.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        Long id,
        Long userId,
        Long roomId,
        LocalDate checkIn,
        LocalDate checkOut,
        BookingStatus status,
        Instant createdAt
) {
}