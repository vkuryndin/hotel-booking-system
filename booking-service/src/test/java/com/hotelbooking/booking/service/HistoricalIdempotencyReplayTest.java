package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.BookingRequestValidationException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoricalIdempotencyReplayTest {
    static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();

    @AfterAll static void close() { VALIDATORS.close(); }

    private BookingService service(HotelServiceClient hotel, BookingSagaStateService state) {
        return new BookingService(hotel, mock(BookingRepository.class),
                mock(BookingMapper.class), state, VALIDATORS.getValidator());
    }

    @Test
    void successfulHistoricalReplayReturnsOriginalBookingWithoutHotelCall() {
        var hotel = mock(HotelServiceClient.class);
        var state = mock(BookingSagaStateService.class);
        var bookingService = service(hotel, state);
        var checkIn = LocalDate.now().minusDays(10);
        var checkOut = LocalDate.now().minusDays(8);
        BookingRequest request = new BookingRequest(1L, null, false,
                checkIn, checkOut, 1, null);
        UUID key = UUID.randomUUID();
        var expected = new BookingResponse(19L, 7L, 1L, checkIn, checkOut,
                BookingStatus.CONFIRMED, Instant.now(), 1);
        when(state.findByIdempotencyKey(7L, key, request)).thenReturn(
                Optional.of(new BookingSagaStateService.ExistingCreation(expected,
                        BookingCreationOutcome.SUCCESS)));

        BookingResponse actual = bookingService.createBooking(7L, request, key);
        assertSame(expected, actual);
        verifyNoInteractions(hotel);
        verify(state, never()).createPending(anyLong(), any(), anyLong(), any());
    }

    @Test
    void newBookingWithPastDatesStillReturnsFieldValidationErrors() {
        var state = mock(BookingSagaStateService.class);
        var bookingService = service(mock(HotelServiceClient.class), state);
        BookingRequest request = new BookingRequest(1L, null, false,
                LocalDate.now().minusDays(3), LocalDate.now().minusDays(1), 1, null);
        var ex = assertThrows(BookingRequestValidationException.class,
                () -> bookingService.createBooking(7L, request, UUID.randomUUID()));
        assertTrue(ex.getFieldErrors().containsKey("checkIn"));
        assertTrue(ex.getFieldErrors().containsKey("checkOut"));
    }

    @Test
    void guestCountStillValidatedForNewBooking() {
        var bookingService = service(mock(HotelServiceClient.class),
                mock(BookingSagaStateService.class));
        BookingRequest request = new BookingRequest(1L, null, false,
                LocalDate.now().plusDays(2), LocalDate.now().plusDays(3), 21, null);
        var ex = assertThrows(BookingRequestValidationException.class,
                () -> bookingService.createBooking(7L, request, UUID.randomUUID()));
        assertTrue(ex.getFieldErrors().containsKey("guestCount"));
    }
}
