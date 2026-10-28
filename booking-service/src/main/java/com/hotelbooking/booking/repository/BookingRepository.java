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

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface BookingRepository extends JpaRepository<Booking, Long> {


    Page<Booking> findByUserIdOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );
    Page<Booking> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Booking> findByStatusOrderByCreatedAtDesc(
            BookingStatus status,
            Pageable pageable
    );
    Optional<Booking> findByUserIdAndIdempotencyKey(
            Long userId,
            UUID idempotencyKey
    );
    List<Booking>
    findTop10ByStatusAndCreationOutcomeInAndCreatedAtBeforeOrderByCreatedAtAsc(
            BookingStatus status,
            List<BookingCreationOutcome> outcomes,
            Instant createdAt
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
    boolean existsByUserId(Long userId);
}