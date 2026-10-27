package com.hotelbooking.booking.exception;

public class RequestInProgressException extends RuntimeException {

    public RequestInProgressException() {
        super("Booking creation is already in progress");
    }
}