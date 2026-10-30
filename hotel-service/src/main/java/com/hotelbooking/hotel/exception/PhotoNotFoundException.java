package com.hotelbooking.hotel.exception;

/** Photo metadata or underlying stored file is absent. */
public final class PhotoNotFoundException extends RuntimeException {
    public PhotoNotFoundException(String message) { super(message); }
}
