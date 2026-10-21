package com.hotelbooking.hotel.exception;

public class HotelNotFoundException extends RuntimeException {

    public HotelNotFoundException(Long hotelId) {
        super("Hotel not found: " + hotelId);
    }
}