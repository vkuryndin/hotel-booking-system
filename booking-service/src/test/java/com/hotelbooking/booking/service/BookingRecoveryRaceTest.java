
package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.InvalidBookingStatusException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false)
@Import({
        BookingSagaStateService.class,
        BookingSagaRecoveryService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingRecoveryRaceTest {
    // These Saga tests use synthetic user IDs; R4 ownership locking is tested separately.
    @MockitoBean AppUserRepository users;
    @BeforeEach void stubUserLookup() {
        when(users.findByIdForUpdate(anyLong())).thenReturn(Optional.of(mock(AppUser.class)));
    }


    @Autowired
    private BookingSagaStateService state;

    @Autowired
    private BookingSagaRecoveryService recovery;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private HotelServiceClient hotel;

    @MockitoBean
    private BookingMapper mapper;

    @Test
    void recoveryWins_confirmationMustFail() throws Exception {

        long id = createOldPendingBooking();

        CountDownLatch releaseStarted = new CountDownLatch(1);
        CountDownLatch allowRelease = new CountDownLatch(1);

        when(hotel.compensateRoom(eq(1L), any(UUID.class)))
                .thenAnswer(invocation -> {
                    releaseStarted.countDown();

                    if (!allowRelease.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Test timeout: compensation was not resumed"
                        );
                    }

                    return null;
                });

        var executor = Executors.newSingleThreadExecutor();

        try {
            var task = executor.submit(recovery::recoverPendingBookings);

            assertTrue(
                    releaseStarted.await(10, TimeUnit.SECONDS),
                    "Recovery did not reach compensateRoom"
            );

            var recovering = bookings.findById(id).orElseThrow();

            assertEquals(
                    BookingCreationOutcome.RECOVERING,
                    recovering.getCreationOutcome()
            );

            assertThrows(
                    InvalidBookingStatusException.class,
                    () -> state.confirm(id),
                    "Confirmation must fail during recovery"
            );

            allowRelease.countDown();
            task.get(10, TimeUnit.SECONDS);

            var result = bookings.findById(id).orElseThrow();

            assertEquals(
                    BookingStatus.CANCELLED,
                    result.getStatus()
            );

            assertEquals(
                    BookingCreationOutcome.FAILED,
                    result.getCreationOutcome()
            );

            verify(hotel, times(1))
                    .compensateRoom(eq(1L), any(UUID.class));

        } finally {
            allowRelease.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void confirmationWins_recoveryMustNotCancel() {

        long id = createOldPendingBooking();

        state.confirm(id);

        recovery.recoverPendingBookings();

        var result = bookings.findById(id).orElseThrow();

        assertEquals(
                BookingStatus.CONFIRMED,
                result.getStatus()
        );

        assertEquals(
                BookingCreationOutcome.SUCCESS,
                result.getCreationOutcome()
        );

        verify(hotel, never())
                .compensateRoom(anyLong(), any(UUID.class));
    }

    private long createOldPendingBooking() {

        var request = new BookingRequest(
                1L,
                null,
                false,
                LocalDate.of(2053, 9, 10),
                LocalDate.of(2053, 9, 12),
                1,
                null
        );

        long id = state.createPending(
                3L,
                request,
                1L,
                UUID.randomUUID()
        ).bookingId();

        Timestamp oldTime = Timestamp.from(
                Instant.now().minus(Duration.ofMinutes(5))
        );

        jdbc.update(
                """
                UPDATE bookings
                SET created_at = ?, saga_updated_at = ?
                WHERE id = ?
                """,
                oldTime,
                oldTime,
                id
        );

        return id;
    }
    @Test
    void activeSagaMustNotBeRecoveredWhenCreatedAtIsOld() {

        long id = createOldPendingBooking();

        // Saga продолжила работу — выбрала другой номер
        state.switchPendingRoom(id, 2L);

        recovery.recoverPendingBookings();

        var booking = bookings.findById(id).orElseThrow();

        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals(
                BookingCreationOutcome.PROCESSING,
                booking.getCreationOutcome()
        );

        verify(hotel, never())
                .compensateRoom(anyLong(), any(UUID.class));
    }

}
