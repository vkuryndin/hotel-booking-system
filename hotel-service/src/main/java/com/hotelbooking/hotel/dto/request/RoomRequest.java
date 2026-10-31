package com.hotelbooking.hotel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.hotel.entity.RoomAmenity;
import com.hotelbooking.hotel.entity.RoomCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

public record RoomRequest(
        @Schema(description = "ID отеля, в котором создаётся номер.") @NotNull @Positive Long hotelId,
        @Schema(description = "Номер комнаты в отеле, до 20 символов.") @NotBlank @Size(max = 20) String number,
        @Schema(description = "Категория комнаты.") RoomCategory category,

        @Schema(description = "Вместимость — положительное целое число.") @Positive
        Integer capacity,

        @Schema(description = "Цена за ночь, неотрицательная.") @DecimalMin("0.00")
        BigDecimal pricePerNight,

        @Schema(description = "Набор удобств комнаты.") Set<RoomAmenity> amenities
) {
}