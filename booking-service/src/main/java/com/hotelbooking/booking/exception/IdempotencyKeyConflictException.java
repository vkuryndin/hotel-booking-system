package com.hotelbooking.booking.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException() {
        super("Idempotency-Key already used with different booking parameters");
    }
}