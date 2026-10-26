package com.hotelbooking.hotel.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RoomReservationReleaseRequest(
        @NotNull UUID reservationId
) {
}