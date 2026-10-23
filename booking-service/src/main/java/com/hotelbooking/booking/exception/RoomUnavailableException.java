package com.hotelbooking.booking.exception;

public class RoomUnavailableException extends RuntimeException {

    public RoomUnavailableException(Long roomId) {
        super("Room is unavailable: " + roomId);
    }
}