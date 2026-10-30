package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.BookingRequest;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Database uniqueness under real concurrent requests with the same (userId, Idempotency-Key). */
@DataJpaTest(showSql = false)
@Import(BookingSagaStateService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingIdempotencyConcurrencyJpaTest {
    // These Saga tests use synthetic user IDs; R4 ownership locking is tested separately.
    @MockitoBean AppUserRepository users;
    @BeforeEach void stubUserLookup() {
        when(users.findByIdForUpdate(anyLong())).thenReturn(Optional.of(mock(AppUser.class)));
    }

    @Autowired BookingSagaStateService state;
    @Autowired BookingRepository repository;
    @MockitoBean BookingMapper mapper;

    @Test
    void sameUserAndKeyCannotCreateTwoPendingBookings() throws Exception {
        UUID key = UUID.randomUUID();
        BookingRequest request = new BookingRequest(1L, null, false,
                LocalDate.of(2055, 10, 10), LocalDate.of(2055, 10, 12), 1, null);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> attempt = () -> {
            assertTrue(start.await(10, TimeUnit.SECONDS));
            try {
                state.createPending(7L, request, 1L, key);
                return true;
            } catch (org.springframework.dao.DataIntegrityViolationException
                     | org.springframework.dao.CannotAcquireLockException ex) {
                return false;
            }
        };
        try {
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            start.countDown();
            long created = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1L, created);
            assertEquals(1L, repository.count());
            assertTrue(repository.findByUserIdAndIdempotencyKey(7L, key).isPresent());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
