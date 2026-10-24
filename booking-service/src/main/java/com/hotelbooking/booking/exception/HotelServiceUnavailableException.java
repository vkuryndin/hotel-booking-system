package com.hotelbooking.booking.exception;

public class HotelServiceUnavailableException extends RuntimeException {

    public HotelServiceUnavailableException(Throwable cause) {
        super("Hotel Service is unavailable", cause);
    }

    public HotelServiceUnavailableException(String message) {
        super(message);
    }
}