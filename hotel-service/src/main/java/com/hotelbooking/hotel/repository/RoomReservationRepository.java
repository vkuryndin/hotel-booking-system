package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.RoomReservation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomReservationRepository extends JpaRepository<RoomReservation, UUID> {
  boolean existsByRoom_Id(Long roomId);

  // Exclude release-before-reserve markers from reservation counts.
  long countByRoom_Hotel_IdAndTombstoneFalse(Long hotelId);

  long countByRoom_Hotel_IdAndStatusAndTombstoneFalse(Long hotelId, ReservationStatus status);

  // Count release-before-reserve markers separately from real reservations.
  long countByRoom_Hotel_IdAndTombstoneTrue(Long hotelId);

  long countByRoom_Hotel_Id(Long hotelId);

  long countByRoom_Hotel_IdAndStatus(Long hotelId, ReservationStatus status);
}
