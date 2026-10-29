package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.RoomReservationConflictException;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationNightRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real separate transactions: two different Saga IDs must not double-book the same nights. */
@DataJpaTest(showSql = false)
@Import(RoomReservationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoomReservationConcurrencyJpaTest {
    @Autowired RoomReservationService reservations;
    @Autowired HotelRepository hotels;
    @Autowired RoomRepository rooms;
    @Autowired RoomReservationNightRepository nights;

    @Test
    void twoSimultaneousDifferentReservationIdsCannotBookOneRoom() throws Exception {
        Hotel hotel = hotels.saveAndFlush(new Hotel("Parallel", "Test Address"));
        long roomId = rooms.saveAndFlush(new Room(hotel, "101")).getId();
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            UUID id = UUID.randomUUID();
            attempts.add(() -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try {
                    reservations.reserve(id, roomId,
                            LocalDate.of(2055, 8, 10), LocalDate.of(2055, 8, 12), 1);
                    return true;
                } catch (RoomReservationConflictException conflict) {
                    return false;
                }
            });
        }
        try {
            var first = executor.submit(attempts.get(0));
            var second = executor.submit(attempts.get(1));
            start.countDown();
            long successes = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1L, successes, "Only one overlapping reservation may succeed");
            assertEquals(2L, nights.count());
            assertEquals(1L, rooms.findById(roomId).orElseThrow().getTimesBooked());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
