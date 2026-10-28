package com.hotelbooking.hotel.service;
import java.time.temporal.ChronoUnit;
import com.hotelbooking.hotel.entity.*;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.exception.RoomReservationConflictException;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.repository.RoomReservationNightRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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


    // Новый метод: бронирование с учётом количества гостей
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

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        var existing = reservationRepository.findById(reservationId);

        if (existing.isPresent()) {
            RoomReservation previous = existing.get();

            if (previous.getStatus() == ReservationStatus.RESERVED
                    && previous.getRoom().getId().equals(roomId)
                    && Objects.equals(previous.getCheckIn(), checkIn)
                    && Objects.equals(previous.getCheckOut(), checkOut)) {
                return previous;
            }

            throw new RoomReservationConflictException(
                    "Reservation ID already used or released: " + reservationId
            );
        }

        // НОВОЕ: проверяем количество гостей
        if (guestCount < 1) {
            throw new IllegalArgumentException(
                    "guestCount must be positive"
            );
        }

        // НОВОЕ: проверяем вместимость номера
        if (room.getCapacity() < guestCount) {
            throw new RoomReservationConflictException(
                    "Room capacity is less than guestCount: " + roomId
            );
        }

        if (!room.isAvailable()) {
            throw new RoomReservationConflictException(
                    "Room is unavailable: " + roomId
            );
        }

        if (nightRepository.existsOccupiedNights(roomId, checkIn, checkOut)) {
            throw new RoomReservationConflictException(
                    "Room is already booked for selected dates: " + roomId
            );
        }

        RoomReservation reservation = reservationRepository.saveAndFlush(
                new RoomReservation(reservationId, room, checkIn, checkOut)
        );

        var nights = checkIn.datesUntil(checkOut)
                .map(date -> new RoomReservationNight(reservation, date))
                .toList();

        nightRepository.saveAllAndFlush(nights);

        room.incrementTimesBooked();

        return reservation;
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
            return reservationRepository.saveAndFlush(
                    RoomReservation.released(reservationId, room)
            );
        }

        if (!reservation.getRoom().getId().equals(roomId)) {
            throw new RoomReservationConflictException(
                    "Reservation belongs to another room"
            );
        }

        if (reservation.getStatus() == ReservationStatus.RESERVED) {
            nightRepository.deleteByReservationId(reservationId);

            if (compensation) {
                room.decrementTimesBooked();
            }

            reservation.release();
        }
        return reservation;
    }
}

