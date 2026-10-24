package com.hotelbooking.booking.exception;

public class InvalidBookingStatusException extends RuntimeException {

    public InvalidBookingStatusException(Long bookingId, String action) {
        super("Cannot " + action + " booking " + bookingId
                + " in its current status");
    }
}