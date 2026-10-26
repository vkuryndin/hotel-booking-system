package com.hotelbooking.booking.dto.client;

import java.util.UUID;

public record HotelReservationReleaseRequest(
        UUID reservationId
) {
}