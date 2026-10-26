package com.hotelbooking.booking.dto.client;

import java.time.LocalDate;
import java.util.UUID;

public record HotelReservationRequest(
        UUID reservationId,
        LocalDate checkIn,
        LocalDate checkOut
) {
}