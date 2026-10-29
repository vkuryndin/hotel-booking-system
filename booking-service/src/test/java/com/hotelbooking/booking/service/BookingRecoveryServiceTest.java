
package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.repository.BookingRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Recovery must be retryable and distinguish creation compensation
 * from normal cancellation.
 */
class BookingRecoveryServiceTest {

    private static final UUID RID =
            UUID.fromString("40000000-0000-0000-0000-000000000004");

    private BookingRepository repository;
    private HotelServiceClient hotel;
    private BookingSagaStateService state;
    private BookingSagaRecoveryService recovery;

    @BeforeEach
    void setUp() {
        repository = mock(BookingRepository.class);
        hotel = mock(HotelServiceClient.class);
        state = mock(BookingSagaStateService.class);

        recovery = new BookingSagaRecoveryService(
                repository,
                hotel,
                state
        );
    }

    private Booking candidateEntity() {
        return candidateEntity(
                11L,
                Instant.parse("2020-01-01T00:00:00Z")
        );
    }

    private Booking candidateEntity(long id, Instant time) {
        Booking booking = mock(Booking.class);

        when(booking.getId()).thenReturn(id);
        when(booking.getSagaUpdatedAt()).thenReturn(time);
        when(booking.getCancellationRequestedAt()).thenReturn(time);

        return booking;
    }

    private BookingSagaStateService.SagaBooking snapshot() {
        return new BookingSagaStateService.SagaBooking(
                11L,
                2L,
                RID,
                LocalDate.of(2055, 1, 1),
                LocalDate.of(2055, 1, 3)
        );
    }

    @Test
    void pendingRecoveryCompensatesBeforeMarkingFailed() {

        Booking candidate = candidateEntity();

        when(repository.findPendingRecoveryCandidates(
                eq(BookingStatus.PENDING),
                anyList(),
                any(Instant.class),
                any(Instant.class),
                eq(BookingSagaStateService.MAX_RECOVERY_ATTEMPTS),
                any(Instant.class),
                anyLong(),
                any(Pageable.class)
        )).thenReturn(List.of(candidate), List.of());

        when(state.prepareRecovery(
                eq(11L),
                any(Instant.class)
        )).thenReturn(Optional.of(snapshot()));

        recovery.recoverPendingBookings();

        var order = inOrder(hotel, state);

        order.verify(hotel).compensateRoom(2L, RID);

        order.verify(state).cancelCreation(
                11L,
                BookingCreationOutcome.FAILED
        );

        verify(hotel, never()).releaseRoom(
                anyLong(),
                any(UUID.class)
        );
    }

    @Test
    void failedCompensationLeavesPendingForNextRecovery() {

        Booking candidate = candidateEntity();

        when(repository.findPendingRecoveryCandidates(
                eq(BookingStatus.PENDING),
                anyList(),
                any(Instant.class),
                any(Instant.class),
                eq(BookingSagaStateService.MAX_RECOVERY_ATTEMPTS),
                any(Instant.class),
                anyLong(),
                any(Pageable.class)
        )).thenReturn(List.of(candidate), List.of());

        when(state.prepareRecovery(
                eq(11L),
                any(Instant.class)
        )).thenReturn(Optional.of(snapshot()));

        when(hotel.compensateRoom(2L, RID))
                .thenThrow(new IllegalStateException("network down"));

        recovery.recoverPendingBookings();

        verify(state, never()).cancelCreation(
                anyLong(),
                any()
        );
    }

    @Test
    void confirmedOrFreshBookingIsSkippedByTransactionalRecheck() {

        Booking candidate = candidateEntity();

        when(repository.findPendingRecoveryCandidates(
                eq(BookingStatus.PENDING),
                anyList(),
                any(Instant.class),
                any(Instant.class),
                eq(BookingSagaStateService.MAX_RECOVERY_ATTEMPTS),
                any(Instant.class),
                anyLong(),
                any(Pageable.class)
        )).thenReturn(List.of(candidate), List.of());

        when(state.prepareRecovery(
                eq(11L),
                any(Instant.class)
        )).thenReturn(Optional.empty());

        recovery.recoverPendingBookings();

        verifyNoInteractions(hotel);
    }

    @Test
    void cancellationRecoveryUsesReleaseNotCompensation() {

        Booking candidate = candidateEntity();

        when(repository.findCancellationRecoveryCandidates(
                eq(BookingStatus.CANCELLING),
                any(Instant.class),
                any(Instant.class),
                eq(BookingSagaStateService.MAX_RECOVERY_ATTEMPTS),
                any(Instant.class),
                anyLong(),
                any(Pageable.class)
        )).thenReturn(List.of(candidate), List.of());

        when(state.prepareCancellationRecovery(
                eq(11L),
                any(Instant.class)
        )).thenReturn(Optional.of(snapshot()));

        recovery.recoverCancellingBookings();

        var order = inOrder(hotel, state);

        order.verify(hotel).releaseRoom(2L, RID);

        order.verify(state).finishCancellation(11L);

        verify(hotel, never()).compensateRoom(
                anyLong(),
                any(UUID.class)
        );
    }

    @Test
    void failedCancellationReleaseLeavesCancellingForNextRecovery() {

        Booking candidate = candidateEntity();

        when(repository.findCancellationRecoveryCandidates(
                eq(BookingStatus.CANCELLING),
                any(Instant.class),
                any(Instant.class),
                eq(BookingSagaStateService.MAX_RECOVERY_ATTEMPTS),
                any(Instant.class),
                anyLong(),
                any(Pageable.class)
        )).thenReturn(List.of(candidate), List.of());

        when(state.prepareCancellationRecovery(
                eq(11L),
                any(Instant.class)
        )).thenReturn(Optional.of(snapshot()));

        when(hotel.releaseRoom(2L, RID))
                .thenThrow(new IllegalStateException("network down"));

        recovery.recoverCancellingBookings();

        verify(state, never()).finishCancellation(anyLong());
    }

    @Test
    void cancellationRecoverySkipsBookingNoLongerCancelling() {

        Booking candidate = candidateEntity();

        when(repository.findCancellationRecoveryCandidates(
                eq(BookingStatus.CANCELLING),
                any(Instant.class),
                any(Instant.class),
                eq(BookingSagaStateService.MAX_RECOVERY_ATTEMPTS),
                any(Instant.class),
                anyLong(),
                any(Pageable.class)
        )).thenReturn(List.of(candidate), List.of());

        when(state.prepareCancellationRecovery(
                eq(11L),
                any(Instant.class)
        )).thenReturn(Optional.empty());

        recovery.recoverCancellingBookings();

        verifyNoInteractions(hotel);
    }

    @Test
    void pendingRecoveryProcessesMoreThanTenCandidates() {

        Instant start = Instant.parse("2020-01-01T00:00:00Z");

        List<Booking> candidates = LongStream.rangeClosed(1, 12)
                .mapToObj(id ->
                        candidateEntity(id, start.plusSeconds(id))
                )
                .toList();

        when(repository.findPendingRecoveryCandidates(
                eq(BookingStatus.PENDING),
                anyList(),
                any(Instant.class),
                any(Instant.class),
                eq(BookingSagaStateService.MAX_RECOVERY_ATTEMPTS),
                any(Instant.class),
                anyLong(),
                any(Pageable.class)
        )).thenReturn(
                candidates.subList(0, 10),
                candidates.subList(10, 12),
                List.of()
        );

        when(state.prepareRecovery(
                anyLong(),
                any(Instant.class)
        )).thenReturn(Optional.empty());

        recovery.recoverPendingBookings();

        verify(state, times(12)).prepareRecovery(
                anyLong(),
                any(Instant.class)
        );

        ArgumentCaptor<Long> cursorIds =
                ArgumentCaptor.forClass(Long.class);

        verify(repository, times(3))
                .findPendingRecoveryCandidates(
                        eq(BookingStatus.PENDING),
                        anyList(),
                        any(Instant.class),
                        any(Instant.class),
                        eq(BookingSagaStateService.MAX_RECOVERY_ATTEMPTS),
                        any(Instant.class),
                        cursorIds.capture(),
                        any(Pageable.class)
                );

        assertEquals(
                List.of(0L, 10L, 12L),
                cursorIds.getAllValues()
        );

        verifyNoInteractions(hotel);
    }

    @Test
    void cancellationRecoveryProcessesMoreThanTenCandidates() {

        Instant start = Instant.parse("2020-01-01T00:00:00Z");

        List<Booking> candidates = LongStream.rangeClosed(1, 12)
                .mapToObj(id ->
                        candidateEntity(id, start.plusSeconds(id))
                )
                .toList();

        when(repository.findCancellationRecoveryCandidates(
                eq(BookingStatus.CANCELLING),
                any(Instant.class),
                any(Instant.class),
                eq(BookingSagaStateService.MAX_RECOVERY_ATTEMPTS),
                any(Instant.class),
                anyLong(),
                any(Pageable.class)
        )).thenReturn(
                candidates.subList(0, 10),
                candidates.subList(10, 12),
                List.of()
        );

        when(state.prepareCancellationRecovery(
                anyLong(),
                any(Instant.class)
        )).thenReturn(Optional.empty());

        recovery.recoverCancellingBookings();

        verify(state, times(12))
                .prepareCancellationRecovery(
                        anyLong(),
                        any(Instant.class)
                );

        ArgumentCaptor<Long> cursorIds =
                ArgumentCaptor.forClass(Long.class);

        verify(repository, times(3))
                .findCancellationRecoveryCandidates(
                        eq(BookingStatus.CANCELLING),
                        any(Instant.class),
                        any(Instant.class),
                        eq(BookingSagaStateService.MAX_RECOVERY_ATTEMPTS),
                        any(Instant.class),
                        cursorIds.capture(),
                        any(Pageable.class)
                );

        assertEquals(
                List.of(0L, 10L, 12L),
                cursorIds.getAllValues()
        );

        verifyNoInteractions(hotel);
    }
}
