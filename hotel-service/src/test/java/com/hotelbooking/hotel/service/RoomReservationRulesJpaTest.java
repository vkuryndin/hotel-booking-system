package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.RoomReservationConflictException;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationNightRepository;
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

/** Hotel Service owns availability and historical booking counts. */
@DataJpaTest(showSql = false)
@Import(RoomReservationService.class)
class RoomReservationRulesJpaTest {
    private static final LocalDate IN = LocalDate.of(2055, 7, 10);
    private static final LocalDate OUT = LocalDate.of(2055, 7, 12);
    @Autowired RoomReservationService reservation;
    @Autowired HotelRepository hotels;
    @Autowired RoomRepository rooms;
    @Autowired RoomReservationRepository reservations;
    @Autowired RoomReservationNightRepository nights;
    @Autowired EntityManager em;
    private Long first;
    private Long second;

    @BeforeEach
    void createRooms() {
        Hotel hotel = hotels.saveAndFlush(new Hotel("Room Rules", "Test Address"));
        first = rooms.saveAndFlush(new Room(hotel, "101")).getId();
        second = rooms.saveAndFlush(new Room(hotel, "102")).getId();
        em.clear();
    }

    private void reserve(UUID id, long room, LocalDate start, LocalDate end) {
        reservation.reserve(id, room, start, end, 1);
    }
    private long counter(long roomId) {
        em.flush();
        em.clear();
        return rooms.findById(roomId).orElseThrow().getTimesBooked();
    }

    @Test void successfulReservationCreatesTwoNightsAndCountsOnce() {
        UUID id = UUID.randomUUID();
        reserve(id, first, IN, OUT);
        assertEquals(2L, nights.count());
        assertEquals(1L, counter(first));
        assertEquals(ReservationStatus.RESERVED, reservations.findById(id).orElseThrow().getStatus());
    }
    @Test void repeatedReserveSameIdIsIdempotent() {
        UUID id = UUID.randomUUID();
        reserve(id, first, IN, OUT);
        reserve(id, first, IN, OUT);
        assertEquals(2L, nights.count());
        assertEquals(1L, counter(first));
    }
    @Test void sameIdCannotBeReusedWithDifferentDatesOrRoom() {
        UUID id = UUID.randomUUID();
        reserve(id, first, IN, OUT);
        assertThrows(RoomReservationConflictException.class,
                () -> reserve(id, first, IN.plusDays(1), OUT.plusDays(1)));
        assertThrows(RoomReservationConflictException.class,
                () -> reserve(id, second, IN, OUT));
        assertEquals(1L, counter(first));
    }
    @Test void overlappingDatesInSameRoomAreRejected() {
        reserve(UUID.randomUUID(), first, IN, OUT);
        assertThrows(RoomReservationConflictException.class,
                () -> reserve(UUID.randomUUID(), first, IN.plusDays(1), OUT.plusDays(1)));
        assertEquals(1L, counter(first));
    }
    @Test void checkoutExclusiveSoAdjacentBookingsAreAllowed() {
        reserve(UUID.randomUUID(), first, IN, OUT);
        reserve(UUID.randomUUID(), first, OUT, OUT.plusDays(2));
        assertEquals(2L, counter(first));
        assertEquals(4L, nights.count());
    }
    @Test void differentRoomsCanBeBookedOverIdenticalDates() {
        reserve(UUID.randomUUID(), first, IN, OUT);
        reserve(UUID.randomUUID(), second, IN, OUT);
        assertEquals(1L, counter(first));
        assertEquals(1L, counter(second));
        assertEquals(4L, nights.count());
    }
    @Test void confirmedCancellationFreesNightsButPreservesHistoricalCount() {
        UUID id = UUID.randomUUID();
        reserve(id, first, IN, OUT);
        reservation.release(id, first, false);
        assertEquals(1L, counter(first));
        assertEquals(0L, nights.count());
        assertEquals(ReservationStatus.RELEASED, reservations.findById(id).orElseThrow().getStatus());
        reserve(UUID.randomUUID(), first, IN, OUT);
        assertEquals(2L, counter(first));
    }
    @Test void compensationRestoresCountAndIsIdempotent() {
        UUID id = UUID.randomUUID();
        reserve(id, first, IN, OUT);
        assertEquals(1L, counter(first));
        reservation.release(id, first, true);
        assertEquals(0L, counter(first));
        assertEquals(0L, nights.count());
        reservation.release(id, first, true);
        assertEquals(0L, counter(first));
    }
    @Test void lateCompensationAfterNormalReleaseDoesNotSubtractHistoricalCount() {
        UUID id = UUID.randomUUID();
        reserve(id, first, IN, OUT);
        reservation.release(id, first, false);
        reservation.release(id, first, false);
        reservation.release(id, first, true);
        assertEquals(1L, counter(first));
    }
    @Test void releaseBeforeReserveCreatesTombstone() {
        UUID id = UUID.randomUUID();
        reservation.release(id, first, true);
        assertEquals(ReservationStatus.RELEASED, reservations.findById(id).orElseThrow().getStatus());
        assertThrows(RoomReservationConflictException.class,
                () -> reserve(id, first, IN, OUT));
        assertEquals(0L, counter(first));
    }
    @Test void releasingOtherRoomReservationIsRejected() {
        UUID id = UUID.randomUUID();
        reserve(id, first, IN, OUT);
        assertThrows(RoomReservationConflictException.class,
                () -> reservation.release(id, second, true));
        assertEquals(1L, counter(first));
        assertEquals(2L, nights.count());
    }
    @Test void unavailableRoomCannotBeReserved() {
        Room room = rooms.findById(first).orElseThrow();
        room.changeAvailability(false);
        rooms.saveAndFlush(room);
        assertThrows(RoomReservationConflictException.class,
                () -> reserve(UUID.randomUUID(), first, IN, OUT));
        assertEquals(0L, counter(first));
    }
    @Test void capacityValidation() {
        assertThrows(RoomReservationConflictException.class,
                () -> reservation.reserve(UUID.randomUUID(), first, IN, OUT, 3));
        assertEquals(0L, counter(first));
    }
    @Test void invalidDateRangeOrMoreThan365Nights() {
        assertThrows(IllegalArgumentException.class,
                () -> reserve(UUID.randomUUID(), first, OUT, IN));
        assertThrows(IllegalArgumentException.class,
                () -> reserve(UUID.randomUUID(), first, IN, IN.plusDays(366)));
        assertEquals(0L, counter(first));
    }
    @Test void roomIsAvailableForPeriodAgainAfterRelease() {
        Long hotelId = rooms.findById(first).orElseThrow().getHotel().getId();
        UUID id = UUID.randomUUID();
        reserve(id, first, IN, OUT);
        assertFalse(rooms.findAvailableForPeriod(hotelId, IN, OUT).stream()
                .anyMatch(r -> r.getId().equals(first)));
        reservation.release(id, first, false);
        em.flush();
        em.clear();
        assertTrue(rooms.findAvailableForPeriod(hotelId, IN, OUT).stream()
                .anyMatch(r -> r.getId().equals(first)));
    }
}
