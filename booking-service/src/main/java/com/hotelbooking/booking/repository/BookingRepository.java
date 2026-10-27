package com.hotelbooking.booking.repository;

import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.hotelbooking.booking.entity.BookingCreationOutcome;
import java.time.Instant;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

import java.time.LocalDate;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            SELECT COUNT(b) > 0
            FROM Booking b
            WHERE b.roomId = :roomId
              AND b.status IN :statuses
              AND b.checkIn < :checkOut
              AND b.checkOut > :checkIn
            """)
    boolean existsOverlappingBooking(
            @Param("roomId") Long roomId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("statuses") List<BookingStatus> statuses
    );
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<Booking> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Booking> findByStatusOrderByCreatedAtDesc(
            BookingStatus status,
            Pageable pageable
    );
    Optional<Booking> findByUserIdAndIdempotencyKey(
            Long userId,
            UUID idempotencyKey
    );
    List<Booking> findTop10ByStatusAndCreationOutcomeAndCreatedAtBeforeOrderByCreatedAtAsc(
            BookingStatus status,
            BookingCreationOutcome creationOutcome,
            Instant createdAt
    );
    boolean existsByUserId(Long userId);
}