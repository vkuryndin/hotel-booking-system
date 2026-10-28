package com.hotelbooking.booking.entity;

public enum BookingCreationOutcome {
    PROCESSING,
    SUCCESS,
    ROOM_CONFLICT,
    FAILED,
    NO_AVAILABLE_ROOMS,
    RECOVERING
}