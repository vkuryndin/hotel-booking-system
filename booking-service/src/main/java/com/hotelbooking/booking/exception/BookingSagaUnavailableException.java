package com.hotelbooking.booking.exception;

public class BookingSagaUnavailableException extends RuntimeException {

    public BookingSagaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}