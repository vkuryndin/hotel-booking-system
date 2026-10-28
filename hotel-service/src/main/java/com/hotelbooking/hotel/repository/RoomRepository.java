package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoomRepository extends
        JpaRepository<Room, Long>,
        JpaSpecificationExecutor<Room> {

    List<Room> findByHotelId(Long hotelId);
    List<Room> findByHotelIdAndAvailableTrue(Long hotelId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
    boolean existsByHotelId(Long hotelId);

    boolean existsByHotelIdAndNumberAndIdNot(
            Long hotelId,
            String number,
            Long id
    );
    @Query("""
        SELECT r FROM Room r
        WHERE r.hotel.id = :hotelId
          AND r.available = true
          AND NOT EXISTS (
              SELECT n.id
              FROM RoomReservationNight n
              WHERE n.room.id = r.id
                AND n.nightDate >= :checkIn
                AND n.nightDate < :checkOut
          )
        """)
    List<Room> findAvailableForPeriod(
            @Param("hotelId") Long hotelId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );
    long countByHotelId(Long hotelId);

    long countByHotelIdAndAvailableTrue(Long hotelId);

    @Query("""
        SELECT COALESCE(SUM(r.timesBooked), 0)
        FROM Room r
        WHERE r.hotel.id = :hotelId
        """)
    Long sumTimesBookedByHotelId(
            @Param("hotelId") Long hotelId
    );
}

