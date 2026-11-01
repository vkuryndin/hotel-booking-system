package com.hotelbooking.hotel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HotelRequest(
    @Schema(description = "Название отеля (обязательно, не более 150 символов).")
        @NotBlank(message = "Hotel name is required")
        @Size(max = 150)
        String name,
    @Schema(description = "Адрес отеля (обязательно, не более 255 символов).")
        @NotBlank(message = "Hotel address is required")
        @Size(max = 255)
        String address) {}
