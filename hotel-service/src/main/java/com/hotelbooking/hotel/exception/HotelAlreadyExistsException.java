package com.hotelbooking.hotel.exception;

public class HotelAlreadyExistsException extends RuntimeException {

    public HotelAlreadyExistsException(String name, String address) {
        super("Hotel already exists: " + name + ", " + address);
    }
}