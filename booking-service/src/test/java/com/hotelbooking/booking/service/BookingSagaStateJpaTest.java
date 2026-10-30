package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.IdempotencyKeyConflictException;
import com.hotelbooking.booking.exception.InvalidBookingStatusException;
import com.hotelbooking.booking.exception.RequestInProgressException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.hotelbooking.booking.repository.AppUserRepository;
import com.hotelbooking.booking.entity.AppUser;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real H2 transactions and row locks, no HTTP or Eureka. */
@DataJpaTest(showSql = false)
@Import(BookingSagaStateService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingSagaStateJpaTest {
    // These Saga tests use synthetic user IDs; R4 ownership locking is tested separately.
    @MockitoBean AppUserRepository users;
    @BeforeEach void stubUserLookup() {
        when(users.findByIdForUpdate(anyLong())).thenReturn(Optional.of(mock(AppUser.class)));
    }

    @Autowired BookingSagaStateService state;
    @Autowired BookingRepository bookings;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean BookingMapper mapper;

    private BookingRequest request() {
        return new BookingRequest(1L, null, false,
                LocalDate.of(2055, 2, 1), LocalDate.of(2055, 2, 3), 1, null);
    }

    private long pending() {
        return state.createPending(6L, request(), 1L, UUID.randomUUID()).bookingId();
    }

    private Booking entity(long id) {
        return bookings.findById(id).orElseThrow();
    }

    private void backdate(long id) {
        Timestamp oldTime = Timestamp.from(
                Instant.now().minus(Duration.ofMinutes(5))
        );

        jdbc.update(
                """
                UPDATE bookings
                SET created_at = ?, saga_updated_at = ?
                WHERE id = ?
                """,
                oldTime, oldTime, id
        );
    }

    @Test
    void createPendingStoresReservationIdFingerprintAndGuestCount() {
        UUID key = UUID.randomUUID();
        var created = state.createPending(6L, request(), 1L, key);
        Booking booking = entity(created.bookingId());
        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals(BookingCreationOutcome.PROCESSING, booking.getCreationOutcome());
        assertEquals(created.reservationId(), booking.getReservationId());
        assertEquals(request().fingerprint(), booking.getRequestFingerprint());
        assertEquals(key, booking.getIdempotencyKey());
        assertEquals(1, booking.getGuestCount());
    }

    @Test
    void pendingCanBeConfirmedOnceOnly() {
        long id = pending();
        state.confirm(id);
        assertEquals(BookingStatus.CONFIRMED, entity(id).getStatus());
        assertEquals(BookingCreationOutcome.SUCCESS, entity(id).getCreationOutcome());
        assertThrows(InvalidBookingStatusException.class, () -> state.confirm(id));
    }

    @Test
    void recoveryMarksPendingRecoveringAndDisablesLateConfirmation() {
        long id = pending();
        backdate(id);
        assertTrue(state.prepareRecovery(id, Instant.now().minusSeconds(120)).isPresent());
        assertEquals(BookingCreationOutcome.RECOVERING, entity(id).getCreationOutcome());
        assertThrows(InvalidBookingStatusException.class, () -> state.confirm(id));
    }

    @Test
    void recoverySkipsYoungPendingAndConfirmed() {
        long id = pending();
        assertTrue(state.prepareRecovery(id, Instant.now().minusSeconds(120)).isEmpty());
        state.confirm(id);
        backdate(id);
        assertTrue(state.prepareRecovery(id, Instant.now().minusSeconds(120)).isEmpty());
    }

    @Test
    void compensationRecordsFailedAndProhibitsConfirmation() {
        long id = pending();
        state.cancelCreation(id, BookingCreationOutcome.FAILED);
        assertEquals(BookingStatus.CANCELLED, entity(id).getStatus());
        assertEquals(BookingCreationOutcome.FAILED, entity(id).getCreationOutcome());
        assertThrows(InvalidBookingStatusException.class, () -> state.confirm(id));
    }

    @Test
    void confirmedBookingCannotBeCompensatedAsFailedCreation() {
        long id = pending();
        state.confirm(id);
        assertThrows(InvalidBookingStatusException.class,
                () -> state.cancelCreation(id, BookingCreationOutcome.FAILED));
        assertEquals(BookingStatus.CONFIRMED, entity(id).getStatus());
    }

    @Test
    void cancelledCreationCannotBeCancelledAgainWithNewOutcome() {
        long id = pending();
        state.cancelCreation(id, BookingCreationOutcome.ROOM_CONFLICT);
        state.cancelCreation(id, BookingCreationOutcome.FAILED);
        assertEquals(BookingCreationOutcome.ROOM_CONFLICT,
                entity(id).getCreationOutcome());
    }

    @Test
    void onlyOwnerOrAdminCanStartCancellation() {
        long id = pending();
        state.confirm(id);
        assertThrows(AccessDeniedException.class,
                () -> state.startCancellation(id, 999L, false));
        assertTrue(state.startCancellation(id, 999L, true).isPresent());
        assertEquals(BookingStatus.CANCELLING, entity(id).getStatus());
    }

    @Test
    void cancellationIsRecoverableAndEventuallyIdempotent() {
        long id = pending();
        state.confirm(id);
        assertTrue(state.startCancellation(id, 6L, false).isPresent());
        assertThrows(RequestInProgressException.class,
                () -> state.startCancellation(id, 6L, false));
        assertTrue(state.prepareCancellationRecovery(id, Instant.now().minusSeconds(120)).isEmpty());
        jdbc.update("UPDATE bookings SET cancellation_requested_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(5))), id);
        assertTrue(state.prepareCancellationRecovery(id, Instant.now().minusSeconds(120)).isPresent());
        state.finishCancellation(id);
        assertEquals(BookingStatus.CANCELLED, entity(id).getStatus());
        state.finishCancellation(id);
        assertTrue(state.startCancellation(id, 6L, false).isEmpty());
    }

    @Test
    void cannotFinishCancellationBeforeItStarted() {
        long id = pending();
        state.confirm(id);
        assertThrows(InvalidBookingStatusException.class,
                () -> state.finishCancellation(id));
    }

    @Test
    void cannotSwitchRoomAfterConfirmationOrWhileRecovering() {
        long confirmedId = pending();
        state.confirm(confirmedId);
        assertThrows(IllegalStateException.class, () -> state.switchPendingRoom(confirmedId, 2L));

        long recoveringId = pending();
        backdate(recoveringId);
        state.prepareRecovery(recoveringId, Instant.now().minusSeconds(120));
        assertThrows(IllegalStateException.class, () -> state.switchPendingRoom(recoveringId, 2L));
    }

    @Test
    void sameKeyDifferentRequestIsRejected() {
        UUID key = UUID.randomUUID();
        state.createPending(6L, request(), 1L, key);
        BookingRequest changed = new BookingRequest(1L, null, false,
                LocalDate.of(2055, 2, 1), LocalDate.of(2055, 2, 4), 1, null);
        assertThrows(IdempotencyKeyConflictException.class,
                () -> state.findByIdempotencyKey(6L, key, changed));
    }

    @Test
    void duplicateUserAndIdempotencyKeyFailsAtDatabaseConstraint() {
        UUID key = UUID.randomUUID();
        state.createPending(6L, request(), 1L, key);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> state.createPending(6L, request(), 1L, key));
    }
}
