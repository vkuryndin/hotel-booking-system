package com.hotelbooking.booking.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record BookingRequest(
        @NotNull @Positive Long roomId,
        @NotNull @FutureOrPresent LocalDate checkIn,
        @NotNull LocalDate checkOut
) {
}