package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomReservation;
import com.hotelbooking.hotel.exception.RoomReservationConflictException;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.repository.RoomReservationNightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoomReservationDuplicateKeyTest {

    private final UUID id = UUID.fromString("11444444-4444-4444-4444-444444444444");
    private final LocalDate checkIn = LocalDate.of(2055, 1, 5);
    private final LocalDate checkOut = LocalDate.of(2055, 1, 7);
    private RoomReservationRepository reservationRepository;
    private RoomReservationService service;

    @BeforeEach
    void setUp() {
        RoomRepository rooms = mock(RoomRepository.class);
        reservationRepository = mock(RoomReservationRepository.class);
        RoomReservationNightRepository nights = mock(RoomReservationNightRepository.class);
        Room room = mock(Room.class);
        when(room.getCapacity()).thenReturn(2);
        when(room.isAvailable()).thenReturn(true);
        when(rooms.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        service = new RoomReservationService(rooms, reservationRepository, nights);
    }

    @Test
    void uniqueKeyViolationBecomesReservationIdConflict() {
        when(reservationRepository.saveAndFlush(any(RoomReservation.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate reservation ID",
                        new SQLException("Duplicate key", "23505")
                ));

        RoomReservationConflictException ex = assertThrows(
                RoomReservationConflictException.class,
                () -> service.reserve(id, 1L, checkIn, checkOut, 1)
        );
        assertEquals("RESERVATION_ID_CONFLICT", ex.getCode());
    }

    @Test
    void foreignKeyViolationIsNotDisguisedAsReservationConflict() {
        when(reservationRepository.saveAndFlush(any(RoomReservation.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Foreign key violation",
                        new SQLException("Foreign key", "23503")
                ));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> service.reserve(id, 1L, checkIn, checkOut, 1)
        );
    }
}
