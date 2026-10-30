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
    @Query("""
        SELECT b FROM Booking b
        WHERE b.status = :status
          AND b.creationOutcome IN :outcomes
          AND b.sagaUpdatedAt < :cutoff
          AND b.recoveryAttempts < :maxAttempts
          AND b.nextRecoveryAt <= :now
          AND (
              b.sagaUpdatedAt > :cursorTime
              OR (
                  b.sagaUpdatedAt = :cursorTime
                  AND b.id > :cursorId
              )
          )
        ORDER BY b.sagaUpdatedAt ASC, b.id ASC
        """)
    List<Booking> findPendingRecoveryCandidates(
            @Param("status") BookingStatus status,
            @Param("outcomes") List<BookingCreationOutcome> outcomes,
            @Param("cutoff") Instant cutoff,
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
        SELECT b FROM Booking b
        WHERE b.status = :status
          AND b.cancellationRequestedAt < :cutoff
          AND b.recoveryAttempts < :maxAttempts
          AND b.nextRecoveryAt <= :now
          AND (
              b.cancellationRequestedAt > :cursorTime
              OR (
                  b.cancellationRequestedAt = :cursorTime
                  AND b.id > :cursorId
              )
          )
        ORDER BY b.cancellationRequestedAt ASC, b.id ASC
        """)
    List<Booking> findCancellationRecoveryCandidates(
            @Param("status") BookingStatus status,
            @Param("cutoff") Instant cutoff,
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            SELECT b FROM Booking b
            WHERE b.recoveryAttempts >= :limit
              AND (b.status = :cancelling
                 OR (b.status = :pending AND b.creationOutcome IN :outcomes))
            ORDER BY b.id ASC
            """)
    List<Booking> findExhaustedRecovery(
            @Param("pending") BookingStatus pending,
            @Param("cancelling") BookingStatus cancelling,
            @Param("outcomes") List<BookingCreationOutcome> outcomes,
            @Param("limit") int limit,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
    boolean existsByUserId(Long userId);
}