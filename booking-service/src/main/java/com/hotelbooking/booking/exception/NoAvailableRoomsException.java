package com.hotelbooking.booking.exception;

public class NoAvailableRoomsException extends RuntimeException {

    public NoAvailableRoomsException() {
        super("No available rooms for selected dates");
    }
}