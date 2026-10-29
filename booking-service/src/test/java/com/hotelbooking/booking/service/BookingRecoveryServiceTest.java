package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Recovery must be retryable and distinguish creation compensation from normal cancellation. */
class BookingRecoveryServiceTest {
    private static final UUID RID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private BookingRepository repository;
    private HotelServiceClient hotel;
    private BookingSagaStateService state;
    private BookingSagaRecoveryService recovery;

    @BeforeEach
    void setUp() {
        repository = mock(BookingRepository.class);
        hotel = mock(HotelServiceClient.class);
        state = mock(BookingSagaStateService.class);
        recovery = new BookingSagaRecoveryService(repository, hotel, state);
    }

    private Booking candidateEntity() {
        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(11L);
        return booking;
    }

    private BookingSagaStateService.SagaBooking snapshot() {
        return new BookingSagaStateService.SagaBooking(11L, 2L, RID,
                LocalDate.of(2055, 1, 1), LocalDate.of(2055, 1, 3));
    }

    @Test
    void pendingRecoveryCompensatesBeforeMarkingFailed() {
        Booking candidate = candidateEntity();
        when(repository.findTop10ByStatusAndCreationOutcomeInAndSagaUpdatedAtBeforeOrderBySagaUpdatedAtAsc(
                eq(BookingStatus.PENDING), anyList(), any(Instant.class)))
                .thenReturn(List.of(candidate));
        when(state.prepareRecovery(eq(11L), any(Instant.class)))
                .thenReturn(Optional.of(snapshot()));

        recovery.recoverPendingBookings();

        var order = inOrder(hotel, state);
        order.verify(hotel).compensateRoom(2L, RID);
        order.verify(state).cancelCreation(11L, BookingCreationOutcome.FAILED);
        verify(hotel, never()).releaseRoom(anyLong(), any(UUID.class));
    }

    @Test
    void failedCompensationLeavesPendingForNextRecovery() {
        Booking candidate = candidateEntity();
        when(repository.findTop10ByStatusAndCreationOutcomeInAndSagaUpdatedAtBeforeOrderBySagaUpdatedAtAsc(
                any(), anyList(), any())).thenReturn(List.of(candidate));
        when(state.prepareRecovery(eq(11L), any())).thenReturn(Optional.of(snapshot()));
        when(hotel.compensateRoom(2L, RID)).thenThrow(new IllegalStateException("network down"));

        recovery.recoverPendingBookings();

        verify(state, never()).cancelCreation(anyLong(), any());
    }

    @Test
    void confirmedOrFreshBookingIsSkippedByTransactionalRecheck() {
        Booking candidate = candidateEntity();
        when(repository.findTop10ByStatusAndCreationOutcomeInAndSagaUpdatedAtBeforeOrderBySagaUpdatedAtAsc(
                any(), anyList(), any())).thenReturn(List.of(candidate));
        when(state.prepareRecovery(eq(11L), any())).thenReturn(Optional.empty());

        recovery.recoverPendingBookings();

        verifyNoInteractions(hotel);
    }

    @Test
    void cancellationRecoveryUsesReleaseNotCompensation() {
        Booking candidate = candidateEntity();
        when(repository.findTop10ByStatusAndCancellationRequestedAtBeforeOrderByCancellationRequestedAtAsc(
                eq(BookingStatus.CANCELLING), any(Instant.class)))
                .thenReturn(List.of(candidate));
        when(state.prepareCancellationRecovery(eq(11L), any()))
                .thenReturn(Optional.of(snapshot()));

        recovery.recoverCancellingBookings();

        var order = inOrder(hotel, state);
        order.verify(hotel).releaseRoom(2L, RID);
        order.verify(state).finishCancellation(11L);
        verify(hotel, never()).compensateRoom(anyLong(), any(UUID.class));
    }

    @Test
    void failedCancellationReleaseLeavesCancellingForNextRecovery() {
        Booking candidate = candidateEntity();
        when(repository.findTop10ByStatusAndCancellationRequestedAtBeforeOrderByCancellationRequestedAtAsc(
                any(), any())).thenReturn(List.of(candidate));
        when(state.prepareCancellationRecovery(eq(11L), any()))
                .thenReturn(Optional.of(snapshot()));
        when(hotel.releaseRoom(2L, RID)).thenThrow(new IllegalStateException("network down"));

        recovery.recoverCancellingBookings();

        verify(state, never()).finishCancellation(anyLong());
    }

    @Test
    void cancellationRecoverySkipsBookingNoLongerCancelling() {
        Booking candidate = candidateEntity();
        when(repository.findTop10ByStatusAndCancellationRequestedAtBeforeOrderByCancellationRequestedAtAsc(
                any(), any())).thenReturn(List.of(candidate));
        when(state.prepareCancellationRecovery(eq(11L), any()))
                .thenReturn(Optional.empty());

        recovery.recoverCancellingBookings();

        verifyNoInteractions(hotel);
    }
}
