package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.dto.client.HotelReservationResponse;
import com.hotelbooking.booking.dto.client.HotelRoomResponse;
import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.BookingSagaUnavailableException;
import com.hotelbooking.booking.exception.NoAvailableRoomsException;
import com.hotelbooking.booking.exception.RequestInProgressException;
import com.hotelbooking.booking.exception.RoomAlreadyBookedException;
import com.hotelbooking.booking.exception.RoomUnavailableException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests of Booking -> Hotel Saga orchestration, without network or Spring context. */
class BookingServiceSagaTest {
    private static final long USER = 7L;
    private static final long BOOKING = 19L;
    private static final long ROOM = 1L;
    private static final UUID KEY = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final LocalDate IN = LocalDate.of(2055, 7, 10);
    private static final LocalDate OUT = LocalDate.of(2055, 7, 12);

    private HotelServiceClient hotel;
    private BookingRepository repository;
    private BookingMapper mapper;
    private BookingSagaStateService state;
    private BookingService service;

    @BeforeEach
    void setUp() {
        hotel = mock(HotelServiceClient.class);
        repository = mock(BookingRepository.class);
        mapper = mock(BookingMapper.class);
        state = mock(BookingSagaStateService.class);
        service = new BookingService(hotel, repository, mapper, state);
    }

    private BookingRequest manual() {
        return new BookingRequest(ROOM, null, false, IN, OUT, 1, null);
    }

    private BookingRequest automatic() {
        return new BookingRequest(null, 10L, true, IN, OUT, 1, null);
    }

    private HotelRoomResponse room(long id) {
        return new HotelRoomResponse(id, 10L, "R" + id, true, 0,
                "STANDARD", 2, BigDecimal.TEN);
    }

    private BookingSagaStateService.SagaBooking pending(long roomId) {
        return new BookingSagaStateService.SagaBooking(BOOKING, roomId, RESERVATION, IN, OUT);
    }

    private BookingResponse confirmed(long roomId) {
        return new BookingResponse(BOOKING, USER, roomId, IN, OUT,
                BookingStatus.CONFIRMED, Instant.parse("2026-10-28T00:00:00Z"), 1);
    }

    private void prepareManualSuccess() {
        when(hotel.getRoom(ROOM)).thenReturn(room(ROOM));
        when(state.createPending(eq(USER), any(BookingRequest.class), eq(ROOM), eq(KEY)))
                .thenReturn(pending(ROOM));
        when(hotel.reserveRoom(eq(ROOM), eq(RESERVATION), eq(IN), eq(OUT), eq(1)))
                .thenReturn(new HotelReservationResponse(RESERVATION, ROOM, IN, OUT, "RESERVED"));
        when(state.confirm(BOOKING)).thenReturn(confirmed(ROOM));
    }

    @Test
    void successfulCreationConfirmsExactlyOnceAndNeverCompensates() {
        prepareManualSuccess();
        assertEquals(BookingStatus.CONFIRMED, service.createBooking(USER, manual(), KEY).status());
        verify(state).confirm(BOOKING);
        verify(hotel, never()).compensateRoom(anyLong(), any(UUID.class));
    }

    @Test
    void replayOfConfirmedKeyReturnsOriginalBookingWithoutAnotherReservation() {
        when(state.findByIdempotencyKey(eq(USER), eq(KEY), any()))
                .thenReturn(Optional.of(new BookingSagaStateService.ExistingCreation(
                        confirmed(ROOM), BookingCreationOutcome.SUCCESS)));
        assertEquals(Long.valueOf(BOOKING), service.createBooking(USER, manual(), KEY).id());
        verifyNoInteractions(hotel);
        verify(state, never()).createPending(anyLong(), any(), anyLong(), any());
    }

    @Test
    void pendingAndRecoveringKeysCannotStartAnotherSaga() {
        for (BookingCreationOutcome outcome : List.of(
                BookingCreationOutcome.PROCESSING, BookingCreationOutcome.RECOVERING)) {
            reset(state);
            when(state.findByIdempotencyKey(eq(USER), eq(KEY), any()))
                    .thenReturn(Optional.of(new BookingSagaStateService.ExistingCreation(
                            confirmed(ROOM), outcome)));
            assertThrows(RequestInProgressException.class,
                    () -> service.createBooking(USER, manual(), KEY));
        }
        verifyNoInteractions(hotel);
    }

    @Test
    void previouslyFailedKeyCannotStartAnotherSaga() {
        when(state.findByIdempotencyKey(eq(USER), eq(KEY), any()))
                .thenReturn(Optional.of(new BookingSagaStateService.ExistingCreation(
                        confirmed(ROOM), BookingCreationOutcome.FAILED)));
        assertThrows(BookingSagaUnavailableException.class,
                () -> service.createBooking(USER, manual(), KEY));
        verifyNoInteractions(hotel);
    }

    @Test
    void repeatedRoomConflictProducesSameDomainError() {
        when(state.findByIdempotencyKey(eq(USER), eq(KEY), any()))
                .thenReturn(Optional.of(new BookingSagaStateService.ExistingCreation(
                        confirmed(ROOM), BookingCreationOutcome.ROOM_CONFLICT)));
        assertThrows(RoomAlreadyBookedException.class,
                () -> service.createBooking(USER, manual(), KEY));
        verifyNoInteractions(hotel);
    }

    @Test
    void hotelConflictCompensatesAndRecordsRoomConflict() {
        prepareManualSuccess();
        when(hotel.reserveRoom(eq(ROOM), eq(RESERVATION), any(), any(), eq(1)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));
        assertThrows(RoomAlreadyBookedException.class,
                () -> service.createBooking(USER, manual(), KEY));
        verify(hotel).compensateRoom(ROOM, RESERVATION);
        verify(state).cancelCreation(BOOKING, BookingCreationOutcome.ROOM_CONFLICT);
        verify(state, never()).confirm(anyLong());
    }

    @Test
    void unavailableHotelCompensatesAndMarksFailed() {
        prepareManualSuccess();
        when(hotel.reserveRoom(eq(ROOM), eq(RESERVATION), any(), any(), eq(1)))
                .thenThrow(new IllegalStateException("Hotel unavailable"));
        assertThrows(BookingSagaUnavailableException.class,
                () -> service.createBooking(USER, manual(), KEY));
        verify(hotel).compensateRoom(ROOM, RESERVATION);
        verify(state).cancelCreation(BOOKING, BookingCreationOutcome.FAILED);
    }

    @Test
    void confirmationFailureAfterHotelReservationStillCompensates() {
        prepareManualSuccess();
        when(state.confirm(BOOKING)).thenThrow(new IllegalStateException("DB unavailable"));
        assertThrows(BookingSagaUnavailableException.class,
                () -> service.createBooking(USER, manual(), KEY));
        verify(hotel).reserveRoom(eq(ROOM), eq(RESERVATION), any(), any(), eq(1));
        verify(hotel).compensateRoom(ROOM, RESERVATION);
        verify(state).cancelCreation(BOOKING, BookingCreationOutcome.FAILED);
    }

    @Test
    void failedCompensationDoesNotMarkSagaCancelledSoRecoveryCanRetry() {
        prepareManualSuccess();
        when(state.confirm(BOOKING)).thenThrow(new IllegalStateException("DB failure"));
        when(hotel.compensateRoom(ROOM, RESERVATION))
                .thenThrow(new IllegalStateException("Hotel still down"));
        assertThrows(BookingSagaUnavailableException.class,
                () -> service.createBooking(USER, manual(), KEY));
        verify(state, never()).cancelCreation(anyLong(), any());
    }

    @Test
    void invalidReservationResponseIsCompensated() {
        prepareManualSuccess();
        when(hotel.reserveRoom(eq(ROOM), eq(RESERVATION), any(), any(), eq(1)))
                .thenReturn(new HotelReservationResponse(UUID.randomUUID(), ROOM, IN, OUT, "RESERVED"));
        assertThrows(BookingSagaUnavailableException.class,
                () -> service.createBooking(USER, manual(), KEY));
        verify(hotel).compensateRoom(ROOM, RESERVATION);
    }

    @Test
    void autoSelectionFallsBackAfterConflictWithoutCompensatingFirstCandidate() {
        when(hotel.recommendRooms(eq(10L), eq(IN), eq(OUT), any(), eq(1)))
                .thenReturn(List.of(room(1L), room(2L)));
        when(state.createPending(eq(USER), any(), eq(1L), eq(KEY)))
                .thenReturn(pending(1L));
        when(state.switchPendingRoom(BOOKING, 2L)).thenReturn(pending(2L));
        when(hotel.reserveRoom(eq(1L), eq(RESERVATION), any(), any(), eq(1)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));
        when(hotel.reserveRoom(eq(2L), eq(RESERVATION), any(), any(), eq(1)))
                .thenReturn(new HotelReservationResponse(RESERVATION, 2L, IN, OUT, "RESERVED"));
        when(state.confirm(BOOKING)).thenReturn(confirmed(2L));
        assertEquals(Long.valueOf(2L), service.createBooking(USER, automatic(), KEY).roomId());
        verify(state).switchPendingRoom(BOOKING, 2L);
        verify(hotel, never()).compensateRoom(anyLong(), any(UUID.class));
    }

    @Test
    void allAutoCandidatesOccupiedCompensatesLatestAttempt() {
        when(hotel.recommendRooms(eq(10L), eq(IN), eq(OUT), any(), eq(1)))
                .thenReturn(List.of(room(1L), room(2L)));
        when(state.createPending(eq(USER), any(), eq(1L), eq(KEY)))
                .thenReturn(pending(1L));
        when(state.switchPendingRoom(BOOKING, 2L)).thenReturn(pending(2L));
        when(hotel.reserveRoom(anyLong(), eq(RESERVATION), any(), any(), eq(1)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));
        assertThrows(NoAvailableRoomsException.class,
                () -> service.createBooking(USER, automatic(), KEY));
        verify(hotel).compensateRoom(2L, RESERVATION);
        verify(state).cancelCreation(BOOKING, BookingCreationOutcome.NO_AVAILABLE_ROOMS);
    }

    @Test
    void cancellationUsesOrdinaryReleaseAndThenFinishes() {
        when(state.startCancellation(BOOKING, USER, false))
                .thenReturn(Optional.of(pending(ROOM)));
        when(state.finishCancellation(BOOKING)).thenReturn(
                new BookingResponse(BOOKING, USER, ROOM, IN, OUT,
                        BookingStatus.CANCELLED, Instant.now(), 1));
        assertEquals(BookingStatus.CANCELLED,
                service.cancelBooking(BOOKING, USER, false).status());
        var ordered = inOrder(hotel, state);
        ordered.verify(hotel).releaseRoom(ROOM, RESERVATION);
        ordered.verify(state).finishCancellation(BOOKING);
        verify(hotel, never()).compensateRoom(anyLong(), any(UUID.class));
    }

    @Test
    void failedReleaseLeavesCancellationInProgressForRecovery() {
        when(state.startCancellation(BOOKING, USER, false))
                .thenReturn(Optional.of(pending(ROOM)));
        when(hotel.releaseRoom(ROOM, RESERVATION))
                .thenThrow(new IllegalStateException("Hotel down"));
        assertThrows(BookingSagaUnavailableException.class,
                () -> service.cancelBooking(BOOKING, USER, false));
        verify(state, never()).finishCancellation(anyLong());
        verify(hotel, never()).compensateRoom(anyLong(), any(UUID.class));
    }

    @Test
    void invalidDatesAndTooLongStayAreRejectedBeforeHotelCall() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(USER,
                        new BookingRequest(ROOM, null, false, OUT, IN, 1, null), KEY));
        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(USER,
                        new BookingRequest(ROOM, null, false, IN, IN.plusDays(366), 1, null), KEY));
        verifyNoInteractions(hotel);
    }

    @Test
    void disabledRoomIsRejectedBeforePendingState() {
        when(hotel.getRoom(ROOM)).thenReturn(new HotelRoomResponse(
                ROOM, 10L, "R1", false, 0, "STANDARD", 2, BigDecimal.TEN));
        assertThrows(RoomUnavailableException.class,
                () -> service.createBooking(USER, manual(), KEY));
        verify(state, never()).createPending(anyLong(), any(), anyLong(), any());
    }

    @Test
    void notEnoughCapacityIsRejectedBeforePendingState() {
        when(hotel.getRoom(ROOM)).thenReturn(room(ROOM));
        assertThrows(IllegalArgumentException.class,
                () -> service.createBooking(USER,
                        new BookingRequest(ROOM, null, false, IN, OUT, 3, null), KEY));
        verify(state, never()).createPending(anyLong(), any(), anyLong(), any());
    }
}
