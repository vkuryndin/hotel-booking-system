package com.hotelbooking.booking.exception;

public class UsernameAlreadyRegisteredException
        extends RuntimeException {

    public UsernameAlreadyRegisteredException() {
        super("Username is already registered");
    }
}