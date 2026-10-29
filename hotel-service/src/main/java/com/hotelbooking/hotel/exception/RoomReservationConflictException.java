package com.hotelbooking.hotel.exception;

public class RoomReservationConflictException extends RuntimeException {

    private final String code;

    public RoomReservationConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public RoomReservationConflictException(String message) {
        this("ROOM_RESERVATION_CONFLICT", message);
    }

    public String getCode() {
        return code;
    }
}