package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.BookingNotFoundException;
import com.hotelbooking.booking.exception.IdempotencyKeyConflictException;
import com.hotelbooking.booking.exception.InvalidBookingStatusException;
import com.hotelbooking.booking.exception.RequestInProgressException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.AppUserRepository;
import com.hotelbooking.booking.repository.BookingRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingSagaStateService {

  private static final Logger log = LoggerFactory.getLogger(BookingSagaStateService.class);

  private final BookingRepository bookingRepository;
  private final AppUserRepository userRepository;
  private final BookingMapper bookingMapper;
  public static final int MAX_RECOVERY_ATTEMPTS = 5;

  public BookingSagaStateService(
      BookingRepository bookingRepository,
      BookingMapper bookingMapper,
      AppUserRepository userRepository) {
    this.bookingRepository = bookingRepository;
    this.bookingMapper = bookingMapper;
    this.userRepository = userRepository;
  }

  public record SagaBooking(
      Long bookingId, Long roomId, UUID reservationId, LocalDate checkIn, LocalDate checkOut) {}

  @Transactional
  public SagaBooking createPending(
      Long userId, BookingRequest request, Long resolvedRoomId, UUID idempotencyKey) {
    // Use the same user-row lock as account deletion to prevent bookings for deleted users.
    userRepository
        .findByIdForUpdate(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));
    Booking booking = new Booking(userId, resolvedRoomId, request.checkIn(), request.checkOut());

    booking.assignIdempotencyKey(idempotencyKey);
    booking.assignRequestFingerprint(request.fingerprint());
    booking.assignGuestCount(request.guestCount());
    bookingRepository.saveAndFlush(booking);

    log.atInfo()
        .addKeyValue("event", "booking.pending")
        .addKeyValue("bookingId", booking.getId())
        .addKeyValue("roomId", booking.getRoomId())
        .addKeyValue("reservationId", booking.getReservationId())
        .addKeyValue("status", "PENDING")
        .log("Booking created in PENDING state");
    return snapshot(booking);
  }

  @Transactional
  public BookingResponse confirm(Long bookingId) {
    Booking booking = find(bookingId);

    try {
      booking.confirm();
      booking.markCreationOutcome(BookingCreationOutcome.SUCCESS);
    } catch (IllegalStateException ex) {
      throw new InvalidBookingStatusException(bookingId, "confirm");
    }

    log.atInfo()
        .addKeyValue("event", "booking.confirmed")
        .addKeyValue("bookingId", booking.getId())
        .addKeyValue("roomId", booking.getRoomId())
        .addKeyValue("reservationId", booking.getReservationId())
        .addKeyValue("fromStatus", "PENDING")
        .addKeyValue("status", "CONFIRMED")
        .log("Booking confirmed");
    return bookingMapper.toResponse(booking);
  }

  @Transactional
  public BookingResponse finishCancellation(Long bookingId) {
    Booking booking =
        bookingRepository
            .findByIdForUpdate(bookingId)
            .orElseThrow(() -> new BookingNotFoundException(bookingId));

    if (booking.getStatus() == BookingStatus.CANCELLED) {
      return bookingMapper.toResponse(booking);
    }

    if (booking.getStatus() != BookingStatus.CANCELLING) {
      throw new InvalidBookingStatusException(bookingId, "cancel");
    }

    booking.cancel();
    log.atInfo()
        .addKeyValue("event", "booking.cancelled")
        .addKeyValue("bookingId", booking.getId())
        .addKeyValue("roomId", booking.getRoomId())
        .addKeyValue("reservationId", booking.getReservationId())
        .addKeyValue("fromStatus", "CANCELLING")
        .addKeyValue("status", "CANCELLED")
        .log("Booking cancellation finalized");
    return bookingMapper.toResponse(booking);
  }

  @Transactional
  public Optional<SagaBooking> startCancellation(Long bookingId, Long userId, boolean isAdmin) {
    Booking booking =
        bookingRepository
            .findByIdForUpdate(bookingId)
            .orElseThrow(() -> new BookingNotFoundException(bookingId));

    if (!isAdmin && !booking.getUserId().equals(userId)) {
      throw new AccessDeniedException("Access denied to booking: " + bookingId);
    }

    if (booking.getStatus() == BookingStatus.CANCELLED) {
      return Optional.empty();
    }

    if (booking.getStatus() == BookingStatus.CANCELLING) {
      throw new RequestInProgressException("Booking cancellation is already in progress");
    }

    if (booking.getStatus() != BookingStatus.CONFIRMED) {
      throw new InvalidBookingStatusException(bookingId, "cancel");
    }

    booking.requestCancellation();
    log.atInfo()
        .addKeyValue("event", "booking.cancelling")
        .addKeyValue("bookingId", booking.getId())
        .addKeyValue("roomId", booking.getRoomId())
        .addKeyValue("reservationId", booking.getReservationId())
        .addKeyValue("fromStatus", "CONFIRMED")
        .addKeyValue("status", "CANCELLING")
        .log("Booking cancellation requested");
    return Optional.of(snapshot(booking));
  }

  public record ExistingCreation(BookingResponse booking, BookingCreationOutcome outcome) {}

  @Transactional(readOnly = true)
  public Optional<ExistingCreation> findByIdempotencyKey(
      Long userId, UUID key, BookingRequest request) {
    return bookingRepository
        .findByUserIdAndIdempotencyKey(userId, key)
        .map(
            booking -> {
              if (!booking.getRequestFingerprint().equals(request.fingerprint())) {
                throw new IdempotencyKeyConflictException();
              }

              return new ExistingCreation(
                  bookingMapper.toResponse(booking), booking.getCreationOutcome());
            });
  }

  private Booking find(Long bookingId) {
    return bookingRepository
        .findById(bookingId)
        .orElseThrow(() -> new BookingNotFoundException(bookingId));
  }

  private SagaBooking snapshot(Booking booking) {
    return new SagaBooking(
        booking.getId(),
        booking.getRoomId(),
        booking.getReservationId(),
        booking.getCheckIn(),
        booking.getCheckOut());
  }

  @Transactional
  public BookingResponse cancelCreation(Long bookingId, BookingCreationOutcome outcome) {
    if (outcome != BookingCreationOutcome.ROOM_CONFLICT
        && outcome != BookingCreationOutcome.ROOM_UNAVAILABLE
        && outcome != BookingCreationOutcome.NO_AVAILABLE_ROOMS
        && outcome != BookingCreationOutcome.FAILED) {
      throw new IllegalArgumentException("Invalid creation outcome");
    }

    Booking booking = find(bookingId);
    if (booking.getStatus() == BookingStatus.CANCELLED) {
      return bookingMapper.toResponse(booking);
    }

    if (booking.getStatus() == BookingStatus.CONFIRMED
        || booking.getCreationOutcome() == BookingCreationOutcome.SUCCESS) {
      throw new InvalidBookingStatusException(bookingId, "compensate");
    }
    booking.cancel();
    booking.markCreationOutcome(outcome);
    log.atInfo()
        .addKeyValue("event", "booking.creation_cancelled")
        .addKeyValue("bookingId", booking.getId())
        .addKeyValue("roomId", booking.getRoomId())
        .addKeyValue("reservationId", booking.getReservationId())
        .addKeyValue("fromStatus", "PENDING")
        .addKeyValue("status", "CANCELLED")
        .addKeyValue("outcome", outcome.name())
        .log("Failed booking creation marked CANCELLED");
    return bookingMapper.toResponse(booking);
  }

  @Transactional
  public SagaBooking switchPendingRoom(Long bookingId, Long newRoomId) {
    Booking booking = find(bookingId);

    booking.switchPendingRoom(newRoomId);
    bookingRepository.flush();

    return snapshot(booking);
  }

  @Transactional
  public Optional<SagaBooking> prepareRecovery(Long bookingId, Instant cutoff) {
    Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);

    if (booking == null
        || booking.getStatus() != BookingStatus.PENDING
        || !booking.getSagaUpdatedAt().isBefore(cutoff)
        || booking.getRecoveryAttempts() >= MAX_RECOVERY_ATTEMPTS
        || booking.getNextRecoveryAt().isAfter(Instant.now())
        || (booking.getCreationOutcome() != BookingCreationOutcome.PROCESSING
            && booking.getCreationOutcome() != BookingCreationOutcome.RECOVERING)) {
      return Optional.empty();
    }

    if (booking.getCreationOutcome() == BookingCreationOutcome.PROCESSING) {
      booking.markCreationOutcome(BookingCreationOutcome.RECOVERING);
    }
    booking.registerRecoveryAttempt();
    return Optional.of(snapshot(booking));
  }

  @Transactional
  public Optional<SagaBooking> prepareCancellationRecovery(Long bookingId, Instant cutoff) {
    Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);

    if (booking == null
        || booking.getStatus() != BookingStatus.CANCELLING
        || booking.getRecoveryAttempts() >= MAX_RECOVERY_ATTEMPTS
        || booking.getNextRecoveryAt().isAfter(Instant.now())
        || booking.getCancellationRequestedAt() == null
        || !booking.getCancellationRequestedAt().isBefore(cutoff)) {
      return Optional.empty();
    }

    booking.registerRecoveryAttempt();
    return Optional.of(snapshot(booking));
  }
}
