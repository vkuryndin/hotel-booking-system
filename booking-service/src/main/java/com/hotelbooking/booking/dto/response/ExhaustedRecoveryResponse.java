package com.hotelbooking.booking.dto.response;

import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import java.time.Instant;

public record ExhaustedRecoveryResponse(
        Long bookingId,
        BookingStatus status,
        BookingCreationOutcome creationOutcome,
        int recoveryAttempts,
        Instant nextRecoveryAt
) {
    public static ExhaustedRecoveryResponse from(Booking booking) {
        return new ExhaustedRecoveryResponse(
                booking.getId(), booking.getStatus(), booking.getCreationOutcome(),
                booking.getRecoveryAttempts(), booking.getNextRecoveryAt()
        );
    }
}
