package com.hotelbooking.hotel.exception;

public class ResourceInUseException extends RuntimeException {

    private final String code;

    public ResourceInUseException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}