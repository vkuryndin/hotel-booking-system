package com.hotelbooking.hotel.service;
import java.time.temporal.ChronoUnit;
import com.hotelbooking.hotel.entity.*;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.exception.RoomReservationConflictException;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.repository.RoomReservationNightRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Service
public class RoomReservationService {

    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;
    private final RoomReservationNightRepository nightRepository;

    public RoomReservationService(
            RoomRepository roomRepository,
            RoomReservationRepository reservationRepository,
            RoomReservationNightRepository nightRepository
    ) {
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.nightRepository = nightRepository;
    }


    @Transactional
    public RoomReservation reserve(
            UUID reservationId,
            Long roomId,
            LocalDate checkIn,
            LocalDate checkOut,
            int guestCount
    ) {
        if (reservationId == null || roomId == null
                || checkIn == null || checkOut == null
                || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Invalid reservation parameters");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > 365) {
            throw new IllegalArgumentException(
                    "Reservation cannot exceed 365 nights"
            );
        }
        if (guestCount < 1) {
            throw new IllegalArgumentException("guestCount must be positive");
        }

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        var existing = reservationRepository.findById(reservationId);

        if (existing.isPresent()) {
            RoomReservation previous = existing.get();

            if (previous.getStatus() == ReservationStatus.RESERVED
                    && previous.getRoom().getId().equals(roomId)
                    && Objects.equals(previous.getCheckIn(), checkIn)
                    && Objects.equals(previous.getCheckOut(), checkOut)
                    && Objects.equals(previous.getGuestCount(), guestCount)) {
                return previous;
            }

            throw new RoomReservationConflictException(
                    previous.getStatus() == ReservationStatus.RELEASED
                            ? "RESERVATION_RELEASED"
                            : "RESERVATION_ID_CONFLICT",
                    "Reservation ID already used or released: " + reservationId
            );
        }

        // Existing reservations are allowed to replay after their check-in
        // date. Reject past dates only when creating a NEW reservation.
        if (checkIn.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("checkIn must be today or later");
        }

        // Проверяем вместимость номера.
        if (room.getCapacity() < guestCount) {
            throw new RoomReservationConflictException(
                    "ROOM_UNAVAILABLE",
                    "Room capacity is less than guestCount: " + roomId
            );
        }

        if (!room.isAvailable()) {
            throw new RoomReservationConflictException(
                    "ROOM_UNAVAILABLE",
                    "Room is unavailable: " + roomId
            );
        }

        if (nightRepository.existsOccupiedNights(roomId, checkIn, checkOut)) {
            throw new RoomReservationConflictException(
                    "ROOM_OCCUPIED",
                    "Room is already booked for selected dates: " + roomId
            );
        }

        RoomReservation reservation = saveNewReservation(
                new RoomReservation(reservationId, room, checkIn, checkOut, guestCount)
        );

        var nights = checkIn.datesUntil(checkOut)
                .map(date -> new RoomReservationNight(reservation, date))
                .toList();

        nightRepository.saveAllAndFlush(nights);

        room.incrementTimesBooked();

        return reservation;
    }

    // A new RoomReservation has one uniqueness rule: its reservationId PK.
    // Used for both regular reservations and release-before-reserve tombstones.
    private RoomReservation saveNewReservation(RoomReservation reservation) {
        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueConstraintViolation(ex)) {
                throw new RoomReservationConflictException(
                        "RESERVATION_ID_CONFLICT",
                        "Reservation ID already used: " + reservation.getReservationId()
                );
            }
            throw ex;
        }
    }

    // SQLSTATE 23505 means unique-constraint violation in H2 and PostgreSQL.
    // Do not convert FK/NOT NULL and other integrity errors into a 409.
    private boolean isUniqueConstraintViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public RoomReservation release(UUID reservationId, Long roomId) {
        return release(reservationId, roomId, false);
    }

    @Transactional
    public RoomReservation release(
            UUID reservationId,
            Long roomId,
            boolean compensation
    ) {

        if (reservationId == null || roomId == null) {
            throw new IllegalArgumentException("Invalid release parameters");
        }

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        RoomReservation reservation = reservationRepository
                .findById(reservationId)
                .orElse(null);

        if (reservation == null) {
            return saveNewReservation(
                    RoomReservation.released(reservationId, room)
            );
        }

        if (!reservation.getRoom().getId().equals(roomId)) {
            throw new RoomReservationConflictException(
                    "RESERVATION_ID_CONFLICT",
                    "Reservation belongs to another room"
            );
        }

        if (reservation.getStatus() == ReservationStatus.RESERVED) {
            nightRepository.deleteByReservationId(reservationId);
            room.decrementTimesBooked();
            reservation.release();
        }
        return reservation;
    }
}

