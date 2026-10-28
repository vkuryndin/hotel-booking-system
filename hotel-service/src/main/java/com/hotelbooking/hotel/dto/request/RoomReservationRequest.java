package com.hotelbooking.hotel.dto.request;

import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

public record RoomReservationRequest(
        UUID reservationId,
        LocalDate checkIn,
        LocalDate checkOut,
        @Positive Integer guestCount
) {
    public RoomReservationRequest {
        guestCount = guestCount == null ? 1 : guestCount;
    }
}