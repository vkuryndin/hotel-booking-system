package com.hotelbooking.hotel.dto.request;

import com.hotelbooking.hotel.entity.HotelAmenity;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;

public record HotelPresentationRequest(

        @Size(max = 3000)
        String description,

        @DecimalMin("0.0")
        @DecimalMax("5.0")
        @Digits(integer = 1, fraction = 1)
        BigDecimal rating,

        Set<HotelAmenity> amenities,

        LocalTime checkInTime,

        LocalTime checkOutTime

) {}