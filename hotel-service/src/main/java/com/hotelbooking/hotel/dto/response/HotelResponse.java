package com.hotelbooking.hotel.dto.response;
import com.hotelbooking.hotel.entity.HotelAmenity;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;

public record HotelResponse(
        Long id,
        String name,
        String address,
        String description,
        BigDecimal rating,
        Set<HotelAmenity> amenities,
        LocalTime checkInTime,
        LocalTime checkOutTime
) {}