package com.hotelbooking.hotel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.hotel.entity.HotelAmenity;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;

public record HotelPresentationRequest(

        @Schema(description = "Описание отеля, до 3000 символов.") @Size(max = 3000)
        String description,

        @Schema(description = "Рейтинг от 0 до 5 с одним знаком после запятой.") @DecimalMin("0.0")
        @DecimalMax("5.0")
        @Digits(integer = 1, fraction = 1)
        BigDecimal rating,

        @Schema(description = "Удобства отеля из перечисления HotelAmenity.") Set<HotelAmenity> amenities,

        @Schema(description = "Стандартное время заезда в формате HH:mm:ss.") LocalTime checkInTime,

        @Schema(description = "Стандартное время выезда в формате HH:mm:ss.") LocalTime checkOutTime

) {}