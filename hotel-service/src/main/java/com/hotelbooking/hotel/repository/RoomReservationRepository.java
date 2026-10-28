package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.RoomReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import com.hotelbooking.hotel.entity.ReservationStatus;

public interface RoomReservationRepository
        extends JpaRepository<RoomReservation, UUID> {
    boolean existsByRoom_Id(Long roomId);
    long countByRoom_Hotel_Id(Long hotelId);

    long countByRoom_Hotel_IdAndStatus(
            Long hotelId,
            ReservationStatus status
    );
}
