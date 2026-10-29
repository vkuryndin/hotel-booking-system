package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.RoomReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import com.hotelbooking.hotel.entity.ReservationStatus;

public interface RoomReservationRepository
        extends JpaRepository<RoomReservation, UUID> {
    boolean existsByRoom_Id(Long roomId);
    // Count only reservations that were actually created (RESERVED at least once).
    long countByRoom_Hotel_IdAndTombstoneFalse(Long hotelId);

    long countByRoom_Hotel_IdAndStatusAndTombstoneFalse(
            Long hotelId,
            ReservationStatus status
    );

    // Diagnostic count: release-before-reserve, not a real reservation.
    long countByRoom_Hotel_IdAndTombstoneTrue(Long hotelId);

    // Kept for compatibility with existing repository callers.
    long countByRoom_Hotel_Id(Long hotelId);

    long countByRoom_Hotel_IdAndStatus(
            Long hotelId,
            ReservationStatus status
    );
}
