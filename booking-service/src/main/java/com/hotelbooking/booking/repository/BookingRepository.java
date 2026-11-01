package com.hotelbooking.booking.repository;

import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

  Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  Page<Booking> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Page<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status, Pageable pageable);

  Optional<Booking> findByUserIdAndIdempotencyKey(Long userId, UUID idempotencyKey);

  @Query(
      "SELECT b FROM Booking b\n"
          + "WHERE b.status = :status\n"
          + "  AND b.creationOutcome IN :outcomes\n"
          + "  AND b.sagaUpdatedAt < :cutoff\n"
          + "  AND b.recoveryAttempts < :maxAttempts\n"
          + "  AND b.nextRecoveryAt <= :now\n"
          + "  AND (\n"
          + "      b.sagaUpdatedAt > :cursorTime\n"
          + "      OR (\n"
          + "          b.sagaUpdatedAt = :cursorTime\n"
          + "          AND b.id > :cursorId\n"
          + "      )\n"
          + "  )\n"
          + "ORDER BY b.sagaUpdatedAt ASC, b.id ASC\n")
  List<Booking> findPendingRecoveryCandidates(
      @Param("status") BookingStatus status,
      @Param("outcomes") List<BookingCreationOutcome> outcomes,
      @Param("cutoff") Instant cutoff,
      @Param("now") Instant now,
      @Param("maxAttempts") int maxAttempts,
      @Param("cursorTime") Instant cursorTime,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  @Query(
      "SELECT b FROM Booking b\n"
          + "WHERE b.status = :status\n"
          + "  AND b.cancellationRequestedAt < :cutoff\n"
          + "  AND b.recoveryAttempts < :maxAttempts\n"
          + "  AND b.nextRecoveryAt <= :now\n"
          + "  AND (\n"
          + "      b.cancellationRequestedAt > :cursorTime\n"
          + "      OR (\n"
          + "          b.cancellationRequestedAt = :cursorTime\n"
          + "          AND b.id > :cursorId\n"
          + "      )\n"
          + "  )\n"
          + "ORDER BY b.cancellationRequestedAt ASC, b.id ASC\n")
  List<Booking> findCancellationRecoveryCandidates(
      @Param("status") BookingStatus status,
      @Param("cutoff") Instant cutoff,
      @Param("now") Instant now,
      @Param("maxAttempts") int maxAttempts,
      @Param("cursorTime") Instant cursorTime,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  @Query(
      "SELECT b FROM Booking b\n"
          + "WHERE b.recoveryAttempts >= :limit\n"
          + "  AND (b.status = :cancelling\n"
          + "     OR (b.status = :pending AND b.creationOutcome IN :outcomes))\n"
          + "ORDER BY b.id ASC\n")
  List<Booking> findExhaustedRecovery(
      @Param("pending") BookingStatus pending,
      @Param("cancelling") BookingStatus cancelling,
      @Param("outcomes") List<BookingCreationOutcome> outcomes,
      @Param("limit") int limit,
      Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM Booking b WHERE b.id = :id")
  Optional<Booking> findByIdForUpdate(@Param("id") Long id);

  boolean existsByUserId(Long userId);
}
