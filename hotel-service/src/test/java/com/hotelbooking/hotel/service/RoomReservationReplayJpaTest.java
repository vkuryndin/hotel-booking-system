package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.controller.RoomReservationController;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomReservation;
import com.hotelbooking.hotel.exception.RoomReservationConflictException;
import com.hotelbooking.hotel.exception.GlobalExceptionHandler;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DataJpaTest(showSql = false)
@Import(RoomReservationService.class)
class RoomReservationReplayJpaTest {

    @Autowired RoomReservationService service;
    @Autowired HotelRepository hotels;
    @Autowired RoomRepository rooms;
    @Autowired RoomReservationRepository reservations;
    @Autowired EntityManager em;

    private long roomId;
    private MockMvc mockMvc;
    private static final LocalDate IN = LocalDate.of(2055, 10, 10);
    private static final LocalDate OUT = LocalDate.of(2055, 10, 12);

    @BeforeEach
    void setUp() {
        Hotel hotel = hotels.saveAndFlush(
                new Hotel("Replay check " + UUID.randomUUID(), "Test Address")
        );
        roomId = rooms.saveAndFlush(new Room(hotel, "101")).getId();
        em.clear();
        mockMvc = MockMvcBuilders.standaloneSetup(new RoomReservationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void guestCountIsPersistedAndIdenticalReplayDoesNotBookAgain() {
        UUID id = UUID.randomUUID();
        service.reserve(id, roomId, IN, OUT, 2);
        em.flush();
        em.clear();

        RoomReservation first = reservations.findById(id).orElseThrow();
        assertEquals(Integer.valueOf(2), first.getGuestCount());

        RoomReservation replay = service.reserve(id, roomId, IN, OUT, 2);
        assertEquals(id, replay.getReservationId());
        assertEquals(Integer.valueOf(2), replay.getGuestCount());

        em.flush();
        em.clear();
        assertEquals(1L, rooms.findById(roomId).orElseThrow().getTimesBooked());
    }

    @Test
    void sameIdAndDatesButDifferentGuestCountReturns409Conflict() {
        UUID id = UUID.randomUUID();
        service.reserve(id, roomId, IN, OUT, 1);

        RoomReservationConflictException ex = assertThrows(
                RoomReservationConflictException.class,
                () -> service.reserve(id, roomId, IN, OUT, 2)
        );
        assertEquals("RESERVATION_ID_CONFLICT", ex.getCode());
        assertEquals(Integer.valueOf(1), reservations.findById(id).orElseThrow().getGuestCount());
        assertEquals(1L, rooms.findById(roomId).orElseThrow().getTimesBooked());
    }

    @Test
    void newReservationWithPastCheckInIsRejected() {
        UUID id = UUID.randomUUID();
        LocalDate yesterday = LocalDate.now().minusDays(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reserve(id, roomId, yesterday, yesterday.plusDays(2), 1)
        );
        assertFalse(reservations.existsById(id));
    }

    @Test
    void alreadyExistingHistoricalReservationCanBeReplayed() {
        UUID id = UUID.randomUUID();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Room room = rooms.findById(roomId).orElseThrow();
        reservations.saveAndFlush(
                new RoomReservation(id, room, yesterday, yesterday.plusDays(1), 1)
        );
        em.clear();

        // Valid replay of a previously accepted booking must remain idempotent.
        RoomReservation replay = service.reserve(
                id, roomId, yesterday, yesterday.plusDays(1), 1
        );
        assertEquals(id, replay.getReservationId());
        assertEquals(Integer.valueOf(1), replay.getGuestCount());
    }

    @Test
    void releasedTombstoneKeepsNullGuestCountAndBlocksReserve() {
        UUID id = UUID.randomUUID();
        service.release(id, roomId, true);
        assertNull(reservations.findById(id).orElseThrow().getGuestCount());

        RoomReservationConflictException ex = assertThrows(
                RoomReservationConflictException.class,
                () -> service.reserve(id, roomId, IN, OUT, 1)
        );
        assertEquals("RESERVATION_RELEASED", ex.getCode());
    }

    @Test
    void internalHttpEndpointReturns200ForReplayAnd409ForChangedGuests() throws Exception {
        UUID id = UUID.randomUUID();
        String url = "/api/rooms/" + roomId + "/confirm-availability";

        String request = """
                {"reservationId":"%s","checkIn":"%s","checkOut":"%s","guestCount":1}
                """.formatted(id, IN, OUT);
        String otherGuestCount = """
                {"reservationId":"%s","checkIn":"%s","checkOut":"%s","guestCount":2}
                """.formatted(id, IN, OUT);

        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk());
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk());
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(otherGuestCount))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_ID_CONFLICT"));
    }

    @Test
    void internalHttpEndpointRejectsPastCheckInWith400() throws Exception {
        UUID id = UUID.randomUUID();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String request = """
                {"reservationId":"%s","checkIn":"%s","checkOut":"%s","guestCount":1}
                """.formatted(id, yesterday, yesterday.plusDays(2));

        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }
}
