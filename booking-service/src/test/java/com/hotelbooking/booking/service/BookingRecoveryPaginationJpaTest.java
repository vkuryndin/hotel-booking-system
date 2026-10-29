package com.hotelbooking.booking.service;

import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.repository.BookingRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingRecoveryPaginationJpaTest {

    @Autowired
    BookingRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private Booking newBooking() {
        Booking booking = new Booking(
                7L,
                1L,
                LocalDate.of(2055, 1, 1),
                LocalDate.of(2055, 1, 3)
        );

        booking.assignIdempotencyKey(UUID.randomUUID());
        booking.assignRequestFingerprint("pagination-test");

        return repository.saveAndFlush(booking);
    }

    @Test
    void pendingQueryReturnsNextPageWithoutSkippingRecords() {

        Instant oldTime = Instant.now()
                .minus(Duration.ofMinutes(5));

        // Создаём 12 зависших PENDING.
        for (int i = 0; i < 12; i++) {
            Booking booking = newBooking();

            jdbc.update(
                    """
                    UPDATE bookings
                    SET saga_updated_at = ?
                    WHERE id = ?
                    """,
                    Timestamp.from(oldTime),
                    booking.getId()
            );
        }

        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofMinutes(2));

        var first = repository.findPendingRecoveryCandidates(
                BookingStatus.PENDING,
                List.of(BookingCreationOutcome.PROCESSING),
                cutoff,
                now,
                5,
                Instant.EPOCH,
                0L,
                PageRequest.of(0, 10)
        );

        assertEquals(10, first.size());

        Booking last = first.get(9);

        var second = repository.findPendingRecoveryCandidates(
                BookingStatus.PENDING,
                List.of(BookingCreationOutcome.PROCESSING),
                cutoff,
                now,
                5,
                last.getSagaUpdatedAt(),
                last.getId(),
                PageRequest.of(0, 10)
        );

        assertEquals(2, second.size());

        // Ни одна бронь второго пакета не была в первом.
        assertTrue(
                second.stream().noneMatch(next ->
                        first.stream().anyMatch(previous ->
                                previous.getId().equals(next.getId())
                        )
                )
        );
    }

    @Test
    void cancellationQueryReturnsNextPageWithoutSkippingRecords() {

        Instant oldTime = Instant.now()
                .minus(Duration.ofMinutes(5));

        // Создаём 12 зависших CANCELLING.
        for (int i = 0; i < 12; i++) {
            Booking booking = newBooking();

            booking.confirm();
            booking.markCreationOutcome(
                    BookingCreationOutcome.SUCCESS
            );

            booking = repository.saveAndFlush(booking);

            booking.requestCancellation();
            booking = repository.saveAndFlush(booking);

            jdbc.update(
                    """
                    UPDATE bookings
                    SET cancellation_requested_at = ?
                    WHERE id = ?
                    """,
                    Timestamp.from(oldTime),
                    booking.getId()
            );
        }

        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofMinutes(2));

        var first = repository.findCancellationRecoveryCandidates(
                BookingStatus.CANCELLING,
                cutoff,
                now,
                5,
                Instant.EPOCH,
                0L,
                PageRequest.of(0, 10)
        );

        assertEquals(10, first.size());

        Booking last = first.get(9);

        var second = repository.findCancellationRecoveryCandidates(
                BookingStatus.CANCELLING,
                cutoff,
                now,
                5,
                last.getCancellationRequestedAt(),
                last.getId(),
                PageRequest.of(0, 10)
        );

        assertEquals(2, second.size());

        assertTrue(
                second.stream().noneMatch(next ->
                        first.stream().anyMatch(previous ->
                                previous.getId().equals(next.getId())
                        )
                )
        );
    }
}