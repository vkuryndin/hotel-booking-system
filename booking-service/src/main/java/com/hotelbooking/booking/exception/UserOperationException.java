package com.hotelbooking.booking.exception;

/** Domain-level user operation failure, independent of HTTP/Spring MVC. */
public final class UserOperationException extends RuntimeException {
    public enum Reason { INVALID_INPUT, USER_NOT_AUTHENTICATED, USER_NOT_FOUND, CONFLICT }
    private final Reason reason;

    public UserOperationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public UserOperationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
