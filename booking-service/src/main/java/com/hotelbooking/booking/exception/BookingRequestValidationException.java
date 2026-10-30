package com.hotelbooking.booking.exception;

import jakarta.validation.ConstraintViolation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class BookingRequestValidationException extends RuntimeException {
    private final Map<String, String> fieldErrors;

    public BookingRequestValidationException(Set<? extends ConstraintViolation<?>> violations) {
        super("Invalid request data");
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : violations) {
            errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }
        this.fieldErrors = Map.copyOf(errors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
