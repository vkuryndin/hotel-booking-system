package com.hotelbooking.booking.dto.client;

public record HotelRoomResponse(
        Long id,
        Long hotelId,
        String number,
        boolean available,
        long timesBooked
) {
}