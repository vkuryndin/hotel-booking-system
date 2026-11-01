package com.hotelbooking.hotel.dto.response;

import com.hotelbooking.hotel.entity.HotelAmenity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;

public record HotelResponse(
    @Schema(description = "Идентификатор отеля.") Long id,
    @Schema(description = "Название отеля.") String name,
    @Schema(description = "Адрес отеля.") String address,
    @Schema(description = "Описание отеля.") String description,
    @Schema(description = "Рейтинг (от 0 до 5).") BigDecimal rating,
    @Schema(description = "Набор удобств отеля.") Set<HotelAmenity> amenities,
    @Schema(description = "Стандартное время заезда.") LocalTime checkInTime,
    @Schema(description = "Стандартное время выезда.") LocalTime checkOutTime) {}
