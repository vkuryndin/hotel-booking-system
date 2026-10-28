package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.exception.BookingNotFoundException;
import com.hotelbooking.booking.exception.InvalidBookingStatusException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hotelbooking.booking.entity.BookingCreationOutcome;

import java.util.Optional;
import com.hotelbooking.booking.exception.IdempotencyKeyConflictException;

import java.time.LocalDate;
import java.util.UUID;
import com.hotelbooking.booking.entity.BookingStatus;
import java.time.Instant;

import com.hotelbooking.booking.exception.RequestInProgressException;
@Service
public class BookingSagaStateService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;

    public BookingSagaStateService(
            BookingRepository bookingRepository,
            BookingMapper bookingMapper
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
    }

    public record SagaBooking(
            Long bookingId,
            Long roomId,
            UUID reservationId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {}

    @Transactional
    public SagaBooking createPending(
            Long userId,
            BookingRequest request,
            Long resolvedRoomId,
            UUID idempotencyKey
    ) {
        Booking booking = new Booking(
                userId,
                resolvedRoomId,
                request.checkIn(),
                request.checkOut()
        );

        booking.assignIdempotencyKey(idempotencyKey);
        booking.assignRequestFingerprint(request.fingerprint());
        booking.assignGuestCount(request.guestCount());
        bookingRepository.saveAndFlush(booking);

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

        return bookingMapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse finishCancellation(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return bookingMapper.toResponse(booking);
        }

        if (booking.getStatus() != BookingStatus.CANCELLING) {
            throw new InvalidBookingStatusException(bookingId, "cancel");
        }

        booking.cancel();
        return bookingMapper.toResponse(booking);
    }

    @Transactional
    public Optional<SagaBooking> startCancellation(
            Long bookingId,
            Long userId,
            boolean isAdmin
    ) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!isAdmin && !booking.getUserId().equals(userId)) {
            throw new AccessDeniedException(
                    "Access denied to booking: " + bookingId
            );
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return Optional.empty();
        }

        if (booking.getStatus() == BookingStatus.CANCELLING) {
            throw new RequestInProgressException(
                    "Booking cancellation is already in progress"
            );
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStatusException(bookingId, "cancel");
        }

        booking.requestCancellation();
        return Optional.of(snapshot(booking));
    }
    public record ExistingCreation(
            BookingResponse booking,
            BookingCreationOutcome outcome
    ) {}

    @Transactional(readOnly = true)
    public Optional<ExistingCreation> findByIdempotencyKey(
            Long userId,
            UUID key,
            BookingRequest request
    ) {
        return bookingRepository.findByUserIdAndIdempotencyKey(userId, key)
                .map(booking -> {
                    if (!booking.getRequestFingerprint()
                            .equals(request.fingerprint())) {
                        throw new IdempotencyKeyConflictException();
                    }

                    return new ExistingCreation(
                            bookingMapper.toResponse(booking),
                            booking.getCreationOutcome()
                    );
                });
    }

    private Booking find(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private SagaBooking snapshot(Booking booking) {
        return new SagaBooking(
                booking.getId(),
                booking.getRoomId(),
                booking.getReservationId(),
                booking.getCheckIn(),
                booking.getCheckOut()
        );
    }
    @Transactional
    public BookingResponse cancelCreation(
            Long bookingId,
            BookingCreationOutcome outcome
    ) {
        if (outcome != BookingCreationOutcome.ROOM_CONFLICT
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

        return bookingMapper.toResponse(booking);
    }
    @Transactional
    public SagaBooking switchPendingRoom(
            Long bookingId,
            Long newRoomId
    ) {
        Booking booking = find(bookingId);

        booking.switchPendingRoom(newRoomId);
        bookingRepository.flush();

        return snapshot(booking);
    }
    @Transactional
    public Optional<SagaBooking> prepareRecovery(
            Long bookingId,
            Instant cutoff
    ) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElse(null);

        if (booking == null
                || booking.getStatus() != BookingStatus.PENDING
                || !booking.getCreatedAt().isBefore(cutoff)
                || (booking.getCreationOutcome() != BookingCreationOutcome.PROCESSING
                && booking.getCreationOutcome() != BookingCreationOutcome.RECOVERING)) {
            return Optional.empty();
        }

        if (booking.getCreationOutcome() == BookingCreationOutcome.PROCESSING) {
            booking.markCreationOutcome(BookingCreationOutcome.RECOVERING);
        }

        return Optional.of(snapshot(booking));
    }
    @Transactional
    public Optional<SagaBooking> prepareCancellationRecovery(
            Long bookingId,
            Instant cutoff
    ) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElse(null);

        if (booking == null
                || booking.getStatus() != BookingStatus.CANCELLING
                || booking.getCancellationRequestedAt() == null
                || !booking.getCancellationRequestedAt().isBefore(cutoff)) {
            return Optional.empty();
        }

        return Optional.of(snapshot(booking));
    }

}