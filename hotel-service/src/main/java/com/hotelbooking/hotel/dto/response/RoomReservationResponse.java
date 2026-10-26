package com.hotelbooking.hotel.dto.response;

import com.hotelbooking.hotel.entity.ReservationStatus;

import java.time.LocalDate;
import java.util.UUID;

public record RoomReservationResponse(
        UUID reservationId,
        Long roomId,
        LocalDate checkIn,
        LocalDate checkOut,
        ReservationStatus status
) {
}