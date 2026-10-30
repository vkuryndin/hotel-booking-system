package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.response.ExhaustedRecoveryResponse;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.BookingNotFoundException;
import com.hotelbooking.booking.exception.InvalidBookingStatusException;
import com.hotelbooking.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ManualBookingRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(ManualBookingRecoveryService.class);
    private final BookingRepository bookings;

    public ManualBookingRecoveryService(BookingRepository bookings) {
        this.bookings = bookings;
    }

    /** A bounded list; never automatically requeues exhausted operations. */
    @Transactional(readOnly = true)
    public List<ExhaustedRecoveryResponse> exhausted() {
        return bookings.findExhaustedRecovery(
                BookingStatus.PENDING, BookingStatus.CANCELLING,
                List.of(BookingCreationOutcome.PROCESSING, BookingCreationOutcome.RECOVERING),
                BookingSagaStateService.MAX_RECOVERY_ATTEMPTS,
                PageRequest.of(0, 100)
        ).stream().map(ExhaustedRecoveryResponse::from).toList();
    }

    /** Requeue only after the automatic limit was reached. The scheduler performs the actual recovery. */
    @Transactional
    public ExhaustedRecoveryResponse requeue(Long bookingId) {
        Booking booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        boolean pending = booking.getStatus() == BookingStatus.PENDING
                && (booking.getCreationOutcome() == BookingCreationOutcome.PROCESSING
                || booking.getCreationOutcome() == BookingCreationOutcome.RECOVERING);
        boolean cancelling = booking.getStatus() == BookingStatus.CANCELLING;
        if ((!pending && !cancelling)
                || booking.getRecoveryAttempts() < BookingSagaStateService.MAX_RECOVERY_ATTEMPTS) {
            throw new InvalidBookingStatusException(bookingId, "manual recovery retry");
        }

        int previousAttempts = booking.getRecoveryAttempts();
        booking.requeueExhaustedRecovery();
        log.warn("Admin requeued exhausted Saga bookingId={}, previousAttempts={}, status={}",
                bookingId, previousAttempts, booking.getStatus());
        return ExhaustedRecoveryResponse.from(booking);
    }
}
