package com.hotelbooking.hotel.dto.request;

import jakarta.validation.constraints.NotNull;

public record RoomAvailabilityRequest(
        @NotNull Boolean available
) {
}