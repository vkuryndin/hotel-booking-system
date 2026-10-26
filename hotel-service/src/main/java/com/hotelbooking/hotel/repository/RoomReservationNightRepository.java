package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.RoomReservationNight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface RoomReservationNightRepository
        extends JpaRepository<RoomReservationNight, Long> {

    @Modifying
    @Query("""
            DELETE FROM RoomReservationNight n
            WHERE n.reservation.reservationId = :reservationId
            """)
    int deleteByReservationId(@Param("reservationId") UUID reservationId);
    @Query("""
    SELECT COUNT(n) > 0
    FROM RoomReservationNight n
    WHERE n.room.id = :roomId
      AND n.nightDate >= :checkIn
      AND n.nightDate < :checkOut
    """)
    boolean existsOccupiedNights(
            @Param("roomId") Long roomId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );
}