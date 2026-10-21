package com.hotelbooking.hotel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RoomRequest(
        @NotNull @Positive Long hotelId,
        @NotBlank @Size(max = 20) String number
) {
}