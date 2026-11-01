package com.hotelbooking.hotel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomUpdateRequest(
    @Schema(description = "Новое обозначение номера (1–20 символов).") @NotBlank @Size(max = 20)
        String number) {}
