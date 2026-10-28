package com.hotelbooking.hotel.dto.response;
import com.hotelbooking.hotel.entity.RoomCategory;
import com.hotelbooking.hotel.entity.RoomAmenity;
import java.math.BigDecimal;
import java.util.Set;

public record RoomResponse(
        Long id,
        Long hotelId,
        String number,
        boolean available,
        long timesBooked,
        RoomCategory category,
        int capacity,
        BigDecimal pricePerNight,
        Set<RoomAmenity> amenities
) {
}