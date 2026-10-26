package com.hotelbooking.booking.dto.client;

import java.time.LocalDate;
import java.util.UUID;

public record HotelReservationResponse(
        UUID reservationId,
        Long roomId,
        LocalDate checkIn,
        LocalDate checkOut,
        String status
) {
}