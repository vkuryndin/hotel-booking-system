package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.dto.client.HotelRoomResponse;
import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.BookingNotFoundException;
import com.hotelbooking.booking.exception.InvalidBookingStatusException;
import com.hotelbooking.booking.exception.RoomAlreadyBookedException;
import com.hotelbooking.booking.exception.RoomUnavailableException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import com.hotelbooking.booking.exception.RequestInProgressException;
import com.hotelbooking.booking.entity.BookingCreationOutcome;


import com.hotelbooking.booking.exception.BookingSagaUnavailableException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;


import java.util.List;
import java.util.UUID;
import com.hotelbooking.booking.exception.NoAvailableRoomsException;
import com.hotelbooking.booking.exception.HotelServiceUnavailableException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.web.client.RestClientException;

@Service
public class BookingService {

    private final HotelServiceClient hotelServiceClient;

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;

    private static final Logger log =
            LoggerFactory.getLogger(BookingService.class);

    private final BookingSagaStateService sagaStateService;

    public BookingService(
            HotelServiceClient hotelServiceClient,
            BookingRepository bookingRepository,
            BookingMapper bookingMapper,

            BookingSagaStateService sagaStateService
    ) {
        this.hotelServiceClient = hotelServiceClient;
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;

        this.sagaStateService = sagaStateService;
    }

    public void validateDates(BookingRequest request) {

        if (request.checkIn() == null || request.checkOut() == null) {
            throw new IllegalArgumentException(
                    "Check-in and check-out dates are required"
            );
        }

        if (!request.checkOut().isAfter(request.checkIn())) {
            throw new IllegalArgumentException(
                    "Check-out must be after check-in"
            );
        }
    }

    public HotelRoomResponse validateRoom(Long roomId) {

        HotelRoomResponse room = hotelServiceClient.getRoom(roomId);


        if (!room.available()) {
            throw new RoomUnavailableException(roomId);
        }

        return room;
    }

    public void validateBooking(BookingRequest request) {
        validateDates(request);

        try {
            validateRoom(request.roomId());

        } catch (ResourceAccessException | HttpServerErrorException ex) {
            throw new BookingSagaUnavailableException(
                    "Hotel Service is unavailable", ex
            );

        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null
                    && ex.getMessage().startsWith("No instances available for ")) {
                throw new BookingSagaUnavailableException(
                        "Hotel Service is unavailable", ex
                );
            }

            throw ex;
        }

        boolean occupied = bookingRepository.existsOverlappingBooking(
                request.roomId(),
                request.checkIn(),
                request.checkOut(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)
        );

        if (occupied) {
            throw new RoomAlreadyBookedException(request.roomId());
        }
    }

    public BookingResponse createBooking(
            Long userId,
            BookingRequest request,
            UUID idempotencyKey
    ) {
        var existing = sagaStateService.findByIdempotencyKey(
                userId, idempotencyKey, request
        );

        if (existing.isPresent()) {
            return waitForExistingRequest(
                    userId, request, idempotencyKey, existing.get()
            );
        }

        validateDates(request);

        List<Long> candidateIds;

        if (request.autoSelect()) {
            if (request.hotelId() == null || request.hotelId() <= 0) {
                throw new IllegalArgumentException(
                        "Valid hotelId is required for autoSelect"
                );
            }

            List<HotelRoomResponse> recommendations;

            try {
                recommendations = hotelServiceClient.recommendRooms(
                        request.hotelId(),
                        request.checkIn(),
                        request.checkOut(),
                        request.strategy(),
                        request.guestCount()
                );

            } catch (RestClientException ex) {
                throw new HotelServiceUnavailableException(ex);
            } catch (IllegalStateException ex) {
                if (ex.getMessage() != null
                        && ex.getMessage().startsWith(
                        "No instances available for ")) {
                    throw new HotelServiceUnavailableException(ex);
                }
                throw ex;
            }

            candidateIds = recommendations.stream()
                    .map(HotelRoomResponse::id)
                    .toList();

            if (candidateIds.isEmpty()) {
                throw new NoAvailableRoomsException();
            }

        } else {
            if (request.roomId() == null || request.roomId() <= 0) {
                throw new IllegalArgumentException(
                        "Valid roomId is required"
                );
            }

            if (request.hotelId() != null) {
                throw new IllegalArgumentException(
                        "hotelId is only allowed with autoSelect=true"
                );
            }

            HotelRoomResponse room = validateRoom(request.roomId());

            if (room.capacity() < request.guestCount()) {
                throw new IllegalArgumentException(
                        "Room capacity is less than guestCount"
                );
            }

            candidateIds = List.of(request.roomId());
        }

        BookingSagaStateService.SagaBooking pending;

        try {
            pending = sagaStateService.createPending(
                    userId,
                    request,
                    candidateIds.get(0),
                    idempotencyKey
            );
        } catch (DataIntegrityViolationException
                 | CannotAcquireLockException ex) {
            return waitForConcurrentCreation(
                    userId, request, idempotencyKey, ex
            );
        }

        for (int i = 0; i < candidateIds.size(); i++) {
            Long selectedRoomId = candidateIds.get(i);

            try {
                if (i > 0) {
                    pending = sagaStateService.switchPendingRoom(
                            pending.bookingId(),
                            selectedRoomId
                    );
                }

                var reservation = hotelServiceClient.reserveRoom(
                        pending.roomId(),
                        pending.reservationId(),
                        pending.checkIn(),
                        pending.checkOut(),
                        request.guestCount()
                );

                if (!"RESERVED".equals(reservation.status())
                        || !pending.reservationId().equals(
                        reservation.reservationId())
                        || !pending.roomId().equals(
                        reservation.roomId())) {
                    throw new IllegalStateException(
                            "Invalid Hotel Service response"
                    );
                }

                return sagaStateService.confirm(pending.bookingId());

            } catch (RuntimeException error) {
                boolean roomConflict =
                        error instanceof RestClientResponseException httpError
                                && httpError.getStatusCode().value() == 409;

                if (request.autoSelect()
                        && roomConflict
                        && i < candidateIds.size() - 1) {

                    log.info(
                            "Auto-selection conflict: bookingId={}, roomId={}. " +
                                    "Trying next candidate.",
                            pending.bookingId(),
                            pending.roomId()
                    );

                    continue;
                }

                return compensateFailedCreation(
                        pending,
                        error,
                        request.autoSelect()
                );
            }
        }

        throw new IllegalStateException(
                "No room selection result"
        );
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(
            Long bookingId,
            Long userId,
            boolean isAdmin
    ) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!isAdmin && !booking.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied to booking: " + bookingId);
        }

        return bookingMapper.toResponse(booking);
    }
    @Transactional
    public BookingResponse confirmBooking(
            Long bookingId,
            Long userId,
            boolean isAdmin
    ) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!isAdmin && !booking.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied to booking: " + bookingId);
        }

        try {
            booking.confirm();
        } catch (IllegalStateException ex) {
            throw new InvalidBookingStatusException(bookingId, "confirm");
        }

        return bookingMapper.toResponse(booking);
    }
    public BookingResponse cancelBooking(
            Long bookingId,
            Long userId,
            boolean isAdmin
    ) {
        var booking = sagaStateService.findAuthorized(
                bookingId, userId, isAdmin
        );

        try {
            hotelServiceClient.releaseRoom(
                    booking.roomId(),
                    booking.reservationId()
            );
        } catch (RuntimeException ex) {
            throw new BookingSagaUnavailableException(
                    "Cannot release reservation for booking: " + bookingId,
                    ex
            );
        }

        return sagaStateService.cancel(bookingId);
    }
    @Transactional(readOnly = true)
    public Page<BookingResponse> getAllBookings(
            boolean isAdmin,
            BookingStatus status,
            Pageable pageable
    ) {
        if (!isAdmin) {
            throw new AccessDeniedException("Admin access required");
        }

        Page<Booking> bookings = status == null
                ? bookingRepository.findAllByOrderByCreatedAtDesc(pageable)
                : bookingRepository.findByStatusOrderByCreatedAtDesc(status, pageable);

        return bookings.map(bookingMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(bookingMapper::toResponse)
                .toList();
    }
    private BookingResponse responseForRepeatedRequest(
            BookingSagaStateService.ExistingCreation existing
    ) {
        BookingResponse booking = existing.booking();

        return switch (existing.outcome()) {case SUCCESS -> new BookingResponse(
                booking.id(),
                booking.userId(),
                booking.roomId(),
                booking.checkIn(),
                booking.checkOut(),
                BookingStatus.CONFIRMED,
                booking.createdAt(),
                booking.guestCount()
        );
            case ROOM_CONFLICT -> throw new RoomAlreadyBookedException(booking.roomId());

            case NO_AVAILABLE_ROOMS -> throw new NoAvailableRoomsException();

            case PROCESSING -> throw new RequestInProgressException();

            case FAILED -> throw new BookingSagaUnavailableException(
                    "Booking creation failed",
                    new IllegalStateException(
                            "Creation outcome: FAILED"
                    )
            );
        };
    }
    private BookingResponse waitForExistingRequest(
            Long userId,
            BookingRequest request,
            UUID idempotencyKey,
            BookingSagaStateService.ExistingCreation existing
    ) {
        var current = existing;

        for (int i = 0;
             i < 100 && current.outcome() == BookingCreationOutcome.PROCESSING;
             i++) {

            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BookingSagaUnavailableException(
                        "Interrupted while waiting for booking", ex
                );
            }

            current = sagaStateService.findByIdempotencyKey(
                    userId, idempotencyKey, request
            ).orElseThrow(() ->
                    new IllegalStateException("Booking disappeared")
            );
        }

        return responseForRepeatedRequest(current);
    }
    private BookingResponse compensateFailedCreation(
            BookingSagaStateService.SagaBooking pending,
            RuntimeException error,
            boolean autoSelect
    ) {
        boolean roomConflict =
                error instanceof RestClientResponseException httpError
                        && httpError.getStatusCode().value() == 409;

        try {
            hotelServiceClient.releaseRoom(
                    pending.roomId(),
                    pending.reservationId()
            );

            BookingCreationOutcome outcome = roomConflict
                    ? (autoSelect
                    ? BookingCreationOutcome.NO_AVAILABLE_ROOMS
                    : BookingCreationOutcome.ROOM_CONFLICT)
                    : BookingCreationOutcome.FAILED;

            sagaStateService.cancelCreation(
                    pending.bookingId(),
                    outcome
            );

        } catch (RuntimeException compensationError) {
            log.error(
                    "Saga compensation failed for booking {}",
                    pending.bookingId(),
                    compensationError
            );

            error.addSuppressed(compensationError);

            throw new BookingSagaUnavailableException(
                    "Booking requires compensation: "
                            + pending.bookingId(),
                    error
            );
        }

        if (roomConflict) {
            if (autoSelect) {
                throw new NoAvailableRoomsException();
            }

            throw new RoomAlreadyBookedException(pending.roomId());
        }

        throw new BookingSagaUnavailableException(
                "Booking cancelled because reservation failed",
                error
        );
    }
    private BookingResponse waitForConcurrentCreation(
            Long userId,
            BookingRequest request,
            UUID idempotencyKey,
            RuntimeException originalError
    ) {
        for (int i = 0; i < 50; i++) {
            var existing = sagaStateService.findByIdempotencyKey(
                    userId, idempotencyKey, request
            );

            if (existing.isPresent()) {
                return waitForExistingRequest(
                        userId, request, idempotencyKey, existing.get()
                );
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BookingSagaUnavailableException(
                        "Interrupted while waiting for concurrent booking",
                        ex
                );
            }
        }

        throw originalError;
    }
}