package com.hotelbooking.booking.exception;

public class RoomAlreadyBookedException extends RuntimeException {

    public RoomAlreadyBookedException(Long roomId) {
        super("Room is already booked for selected dates: " + roomId);
    }
}