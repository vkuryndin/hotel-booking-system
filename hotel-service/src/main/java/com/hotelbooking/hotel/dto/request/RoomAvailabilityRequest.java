package com.hotelbooking.hotel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

public record RoomAvailabilityRequest(
        @Schema(description = "Признак административной доступности номера.") @NotNull Boolean available
) {
}