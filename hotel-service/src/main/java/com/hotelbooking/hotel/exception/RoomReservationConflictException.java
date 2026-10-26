package com.hotelbooking.hotel.exception;

public class RoomReservationConflictException extends RuntimeException {

    public RoomReservationConflictException(String message) {
        super(message);
    }
}