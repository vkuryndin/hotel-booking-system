package com.hotelbooking.booking.dto.client;

import java.math.BigDecimal;

public record HotelRoomResponse(
        Long id,
        Long hotelId,
        String number,
        boolean available,
        long timesBooked,
        String category,
        int capacity,
        BigDecimal pricePerNight
) {
}