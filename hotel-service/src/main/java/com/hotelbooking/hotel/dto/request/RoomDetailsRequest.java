package com.hotelbooking.hotel.dto.request;

import com.hotelbooking.hotel.entity.RoomAmenity;
import com.hotelbooking.hotel.entity.RoomCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Set;

public record RoomDetailsRequest(
        RoomCategory category,

        @Positive
        Integer capacity,

        @DecimalMin("0.00")
        BigDecimal pricePerNight,

        Set<RoomAmenity> amenities
) {}