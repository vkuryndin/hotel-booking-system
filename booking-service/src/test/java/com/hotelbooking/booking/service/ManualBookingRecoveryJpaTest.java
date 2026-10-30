package com.hotelbooking.booking.service;

import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.InvalidBookingStatusException;
import com.hotelbooking.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import(ManualBookingRecoveryService.class)
class ManualBookingRecoveryJpaTest {
    @Autowired BookingRepository repository;
    @Autowired ManualBookingRecoveryService manual;

    private Booking create() {
        Booking booking = new Booking(1L, 2L,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(11));
        booking.assignRequestFingerprint("manual-recovery-test");
        booking.assignIdempotencyKey(UUID.randomUUID());
        return repository.saveAndFlush(booking);
    }

    private void exhaust(Booking booking) {
        for (int i = 0; i < BookingSagaStateService.MAX_RECOVERY_ATTEMPTS; i++) {
            booking.registerRecoveryAttempt();
        }
        repository.saveAndFlush(booking);
    }

    @Test
    void requeuesPendingWithoutLosingReservationIdentity() {
        Booking booking = create();
        var uuid = booking.getReservationId();
        exhaust(booking);
        assertEquals(1, manual.exhausted().size());

        var result = manual.requeue(booking.getId());
        assertEquals(0, result.recoveryAttempts());
        assertEquals(BookingStatus.PENDING, result.status());
        Booking reloaded = repository.findById(booking.getId()).orElseThrow();
        assertEquals(uuid, reloaded.getReservationId());
        assertEquals(BookingCreationOutcome.PROCESSING, reloaded.getCreationOutcome());
        assertTrue(manual.exhausted().isEmpty());
        assertThrows(InvalidBookingStatusException.class, () -> manual.requeue(booking.getId()));
    }

    @Test
    void requeuesCancellingButPreservesStatusAndBookingId() {
        Booking booking = create();
        booking.confirm();
        booking.markCreationOutcome(BookingCreationOutcome.SUCCESS);
        booking.requestCancellation();
        exhaust(booking);
        assertEquals(1, manual.exhausted().size());
        assertEquals(BookingStatus.CANCELLING, manual.requeue(booking.getId()).status());
        assertEquals(0, repository.findById(booking.getId()).orElseThrow().getRecoveryAttempts());
    }

    @Test
    void rejectsManualRequeueBeforeLimitOrAfterCompletion() {
        Booking pending = create();
        assertThrows(InvalidBookingStatusException.class, () -> manual.requeue(pending.getId()));
        Booking confirmed = create();
        confirmed.confirm();
        confirmed.markCreationOutcome(BookingCreationOutcome.SUCCESS);
        exhaust(confirmed);
        assertTrue(manual.exhausted().isEmpty());
        assertThrows(InvalidBookingStatusException.class, () -> manual.requeue(confirmed.getId()));
    }
}
