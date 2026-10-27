package com.hotelbooking.hotel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomUpdateRequest(
        @NotBlank
        @Size(max = 20)
        String number
) {
}