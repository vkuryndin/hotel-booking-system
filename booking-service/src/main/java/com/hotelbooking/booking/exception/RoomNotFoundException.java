package com.hotelbooking.booking.exception;

public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(Long roomId) {
        super("Room not found: " + roomId);
    }
}