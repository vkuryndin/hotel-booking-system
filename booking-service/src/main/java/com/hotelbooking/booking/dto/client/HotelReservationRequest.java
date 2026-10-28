package com.hotelbooking.booking.dto.client;

import java.time.LocalDate;
import java.util.UUID;

public record HotelReservationRequest(
        UUID reservationId,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer guestCount
) {
    public HotelReservationRequest {
        guestCount = guestCount == null ? 1 : guestCount;
    }

    public HotelReservationRequest(
            UUID reservationId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        this(reservationId, checkIn, checkOut, 1);
    }
}