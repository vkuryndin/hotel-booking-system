package com.hotelbooking.hotel.dto.request;

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
        @NotNull @Positive Long hotelId,
        @NotBlank @Size(max = 20) String number,
        RoomCategory category,

        @Positive
        Integer capacity,

        @DecimalMin("0.00")
        BigDecimal pricePerNight,

        Set<RoomAmenity> amenities
) {
}