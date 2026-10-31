package com.hotelbooking.hotel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.hotelbooking.hotel.entity.RoomCategory;
import com.hotelbooking.hotel.entity.RoomAmenity;
import java.math.BigDecimal;
import java.util.Set;

public record RoomResponse(
        @Schema(description = "Идентификатор номера.") Long id,
        @Schema(description = "Идентификатор отеля.") Long hotelId,
        @Schema(description = "Обозначение номера внутри отеля.") String number,
        @Schema(description = "Административный признак доступности.") boolean available,
        @Schema(description = "Число действующих подтверждённых бронирований номера.") long timesBooked,
        @Schema(description = "Категория номера.") RoomCategory category,
        @Schema(description = "Максимальное количество гостей.") int capacity,
        @Schema(description = "Цена за одну ночь.") BigDecimal pricePerNight,
        @Schema(description = "Удобства номера.") Set<RoomAmenity> amenities
) {
}