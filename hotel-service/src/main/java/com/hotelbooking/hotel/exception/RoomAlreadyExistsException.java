package com.hotelbooking.hotel.exception;

public class RoomAlreadyExistsException extends RuntimeException {

    public RoomAlreadyExistsException(Long hotelId, String number) {
        super("Room " + number + " already exists in hotel " + hotelId);
    }
}