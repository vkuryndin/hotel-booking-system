package com.hotelbooking.booking.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record BookingRequest(
        Long roomId,
        Long hotelId,
        Boolean autoSelect,
        @NotNull @FutureOrPresent LocalDate checkIn,
        @NotNull @Future LocalDate checkOut,
        @Positive @Max(20) Integer guestCount,
        RoomSelectionMode strategy
)
{
    public BookingRequest {
        autoSelect = Boolean.TRUE.equals(autoSelect);
        guestCount = guestCount == null ? 1 : guestCount;
        strategy = strategy == null
                ? RoomSelectionMode.LEAST_BOOKED
                : strategy;
    }
    public String fingerprint() {
        if (Boolean.TRUE.equals(autoSelect)) {
            return "AUTO:"
                    + hotelId + ":"
                    + checkIn + ":"
                    + checkOut + ":"
                    + guestCount + ":"
                    + strategy;
        }

        return "ROOM:"
                + roomId + ":"
                + checkIn + ":"
                + checkOut + ":"
                + guestCount;
    }
}