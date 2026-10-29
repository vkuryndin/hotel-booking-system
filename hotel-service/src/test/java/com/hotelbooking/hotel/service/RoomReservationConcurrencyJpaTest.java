package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.RoomReservationConflictException;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationNightRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    @Autowired RoomReservationRepository reservationRepository;

    @Test
    void twoSimultaneousDifferentReservationIdsCannotBookOneRoom() throws Exception {
        Hotel hotel = hotels.saveAndFlush(new Hotel("Parallel", "Test Address"));
        long roomId = rooms.saveAndFlush(new Room(hotel, "101")).getId();
        long initialNightCount = nights.count();
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
            assertEquals(initialNightCount + 2L, nights.count());
            assertEquals(1L, rooms.findById(roomId).orElseThrow().getTimesBooked());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void sameReservationIdForDifferentRoomsReturnsConflict() throws Exception {
        Hotel hotel = hotels.saveAndFlush(new Hotel("Parallel Same ID", "Test Address 2"));
        long firstRoomId = rooms.saveAndFlush(new Room(hotel, "201")).getId();
        long secondRoomId = rooms.saveAndFlush(new Room(hotel, "202")).getId();
        long initialNightCount = nights.count();
        long initialReservationCount = reservationRepository.count();
        UUID sameId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2055, 9, 10);
        LocalDate endDate = LocalDate.of(2055, 9, 12);

        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<String> reserveFirst = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try {
                    reservations.reserve(sameId, firstRoomId, startDate, endDate, 1);
                    return "SUCCESS";
                } catch (RoomReservationConflictException ex) {
                    return ex.getCode();
                }
            };
            Callable<String> reserveSecond = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try {
                    reservations.reserve(sameId, secondRoomId, startDate, endDate, 1);
                    return "SUCCESS";
                } catch (RoomReservationConflictException ex) {
                    return ex.getCode();
                }
            };

            var firstResult = executor.submit(reserveFirst);
            var secondResult = executor.submit(reserveSecond);
            start.countDown();

            assertEquals(
                    Set.of("SUCCESS", "RESERVATION_ID_CONFLICT"),
                    Set.of(firstResult.get(20, TimeUnit.SECONDS),
                            secondResult.get(20, TimeUnit.SECONDS))
            );
            assertEquals(initialReservationCount + 1, reservationRepository.count());
            assertEquals(initialNightCount + 2, nights.count());
            assertEquals(
                    1L,
                    rooms.findById(firstRoomId).orElseThrow().getTimesBooked()
                            + rooms.findById(secondRoomId).orElseThrow().getTimesBooked()
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentReleasesForDifferentRoomsUseOneTombstone() throws Exception {
        Hotel hotel = hotels.saveAndFlush(new Hotel("Parallel Tombstone", "Test Address 3"));
        long firstRoomId = rooms.saveAndFlush(new Room(hotel, "301")).getId();
        long secondRoomId = rooms.saveAndFlush(new Room(hotel, "302")).getId();
        long initialReservationCount = reservationRepository.count();
        long initialNightCount = nights.count();
        UUID sameId = UUID.randomUUID();

        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<String> first = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try {
                    reservations.release(sameId, firstRoomId, true);
                    return "SUCCESS";
                } catch (RoomReservationConflictException ex) {
                    return ex.getCode();
                }
            };
            Callable<String> second = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try {
                    reservations.release(sameId, secondRoomId, true);
                    return "SUCCESS";
                } catch (RoomReservationConflictException ex) {
                    return ex.getCode();
                }
            };
            var result1 = executor.submit(first);
            var result2 = executor.submit(second);
            start.countDown();

            assertEquals(
                    Set.of("SUCCESS", "RESERVATION_ID_CONFLICT"),
                    Set.of(result1.get(20, TimeUnit.SECONDS),
                           result2.get(20, TimeUnit.SECONDS))
            );
            assertEquals(initialReservationCount + 1, reservationRepository.count());
            assertEquals(initialNightCount, nights.count());
            assertEquals(0L,
                    rooms.findById(firstRoomId).orElseThrow().getTimesBooked()
                    + rooms.findById(secondRoomId).orElseThrow().getTimesBooked());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
