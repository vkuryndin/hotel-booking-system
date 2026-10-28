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
    public BookingResponse cancel(Long bookingId) {
        Booking booking = find(bookingId);
        booking.cancel();
        return bookingMapper.toResponse(booking);
    }

    @Transactional(readOnly = true)
    public SagaBooking findAuthorized(
            Long bookingId,
            Long userId,
            boolean isAdmin
    ) {
        Booking booking = find(bookingId);

        if (!isAdmin && !booking.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied to booking: " + bookingId);
        }

        return snapshot(booking);
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

}