package com.hotelbooking.booking.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record BookingRequest(
        Long roomId,
        Long hotelId,
        Boolean autoSelect,
        @NotNull @FutureOrPresent LocalDate checkIn,
        @NotNull LocalDate checkOut
)
{
    public BookingRequest {
        autoSelect = Boolean.TRUE.equals(autoSelect);
    }
    public String fingerprint() {
        String selection = autoSelect
                ? "AUTO:" + hotelId
                : "ROOM:" + roomId;

        return selection + ":" + checkIn + ":" + checkOut;
    }
}