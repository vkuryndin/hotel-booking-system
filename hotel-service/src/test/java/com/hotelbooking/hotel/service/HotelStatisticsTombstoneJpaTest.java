package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.response.HotelStatisticsResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.RoomReservationConflictException;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import({RoomReservationService.class, HotelStatisticsService.class})
class HotelStatisticsTombstoneJpaTest {

    @Autowired private RoomReservationService reservationService;
    @Autowired private HotelStatisticsService statisticsService;
    @Autowired private HotelRepository hotels;
    @Autowired private RoomRepository rooms;
    @Autowired private RoomReservationRepository reservations;
    @Autowired private EntityManager em;

    private Long hotelId;
    private Long roomId;
    private LocalDate checkIn;

    @BeforeEach
    void setUp() {
        var hotel = hotels.saveAndFlush(
                new Hotel("Stage16 " + UUID.randomUUID(), "Test Address")
        );
        hotelId = hotel.getId();
        roomId = rooms.saveAndFlush(new Room(hotel, "101")).getId();
        checkIn = LocalDate.now().plusDays(30);
        em.clear();
    }

    private HotelStatisticsResponse stats() {
        em.flush();
        em.clear();
        return statisticsService.getStatistics(hotelId);
    }

    @Test
    void releaseBeforeReserveIsTombstoneAndNotARealCancellation() {
        UUID id = UUID.randomUUID();

        reservationService.release(id, roomId, true);
        reservationService.release(id, roomId, true); // idempotent

        var reservation = reservations.findById(id).orElseThrow();
        assertTrue(reservation.isTombstone());
        assertEquals(ReservationStatus.RELEASED, reservation.getStatus());
        assertNull(reservation.getGuestCount());

        var statistics = stats();
        assertEquals(0L, statistics.totalReservations());
        assertEquals(0L, statistics.reservedReservations());
        assertEquals(0L, statistics.releasedReservations());
        assertEquals(1L, statistics.tombstoneReservations());
        assertEquals(0L, statistics.totalTimesBooked());

        var conflict = assertThrows(RoomReservationConflictException.class,
                () -> reservationService.reserve(id, roomId, checkIn, checkIn.plusDays(2), 1));
        assertEquals("RESERVATION_RELEASED", conflict.getCode());
    }

    @Test
    void completedCancellationCountsAsReleasedNotAsTombstone() {
        UUID id = UUID.randomUUID();
        reservationService.reserve(id, roomId, checkIn, checkIn.plusDays(2), 1);
        reservationService.release(id, roomId, false);

        assertFalse(reservations.findById(id).orElseThrow().isTombstone());

        var statistics = stats();
        assertEquals(1L, statistics.totalReservations());
        assertEquals(0L, statistics.reservedReservations());
        assertEquals(1L, statistics.releasedReservations());
        assertEquals(0L, statistics.tombstoneReservations());
        assertEquals(0L, statistics.totalTimesBooked());
    }

    @Test
    void businessCountersExcludeTechnicalTombstonesInMixedScenario() {
        UUID activeId = UUID.randomUUID();
        UUID cancelledId = UUID.randomUUID();
        UUID compensationId = UUID.randomUUID();
        UUID tombstoneId = UUID.randomUUID();

        reservationService.reserve(activeId, roomId, checkIn, checkIn.plusDays(2), 1);
        reservationService.reserve(cancelledId, roomId, checkIn.plusDays(3), checkIn.plusDays(5), 1);
        reservationService.release(cancelledId, roomId, false);

        reservationService.reserve(compensationId, roomId,
                checkIn.plusDays(6), checkIn.plusDays(8), 1);
        reservationService.release(compensationId, roomId, true);

        reservationService.release(tombstoneId, roomId, true);

        var statistics = stats();
        assertEquals(3L, statistics.totalReservations());
        assertEquals(1L, statistics.reservedReservations());
        assertEquals(2L, statistics.releasedReservations());
        assertEquals(1L, statistics.tombstoneReservations());
        assertEquals(1L, statistics.totalTimesBooked());

        // The underlying technical row must remain to block delayed Saga calls.
        assertTrue(reservations.existsById(tombstoneId));
        assertEquals(4L, reservations.countByRoom_Hotel_Id(hotelId));
    }
}
