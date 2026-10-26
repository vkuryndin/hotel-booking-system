package com.hotelbooking.hotel.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record RoomReservationRequest(
        @NotNull UUID reservationId,
        @NotNull @FutureOrPresent LocalDate checkIn,
        @NotNull LocalDate checkOut
) {
}