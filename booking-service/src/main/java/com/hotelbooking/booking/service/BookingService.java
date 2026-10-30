package com.hotelbooking.booking.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import com.hotelbooking.booking.exception.BookingRequestValidationException;
import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.dto.client.HotelRoomResponse;
import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.BookingNotFoundException;
import com.hotelbooking.booking.exception.RoomAlreadyBookedException;
import com.hotelbooking.booking.exception.RoomUnavailableException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;


import com.hotelbooking.booking.exception.RequestInProgressException;
import com.hotelbooking.booking.entity.BookingCreationOutcome;


import com.hotelbooking.booking.exception.BookingSagaUnavailableException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.List;
import java.util.UUID;
import com.hotelbooking.booking.exception.NoAvailableRoomsException;
import com.hotelbooking.booking.exception.HotelServiceUnavailableException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.web.client.RestClientException;
import java.time.temporal.ChronoUnit;
import tools.jackson.databind.json.JsonMapper;

@Service
public class BookingService {

    private final HotelServiceClient hotelServiceClient;

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;

    private static final JsonMapper HOTEL_ERROR_MAPPER =
            JsonMapper.builder().build();
    private static final Logger log =
            LoggerFactory.getLogger(BookingService.class);

    private final BookingSagaStateService sagaStateService;
    private final Validator validator;

    public BookingService(
            HotelServiceClient hotelServiceClient,
            BookingRepository bookingRepository,
            BookingMapper bookingMapper,

            BookingSagaStateService sagaStateService,
            Validator validator
    ) {
        this.hotelServiceClient = hotelServiceClient;
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;

        this.sagaStateService = sagaStateService;
        this.validator = validator;
    }

    private void validateDates(BookingRequest request) {

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
        if (ChronoUnit.DAYS.between(request.checkIn(), request.checkOut()) > 365) {
            throw new IllegalArgumentException(
                    "Booking cannot exceed 365 nights"
            );
        }
    }

    private HotelRoomResponse validateRoom(Long roomId) {

        HotelRoomResponse room = hotelServiceClient.getRoom(roomId);


        if (!room.available()) {
            throw new RoomUnavailableException(roomId);
        }

        return room;
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
            return responseForRepeatedRequest(existing.get());
        }

        // MVC does not pre-validate this endpoint: an exact Idempotency-Key replay
        // must succeed even after the original check-in/check-out have passed.
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new BookingRequestValidationException(violations);
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

            var concurrent = sagaStateService.findByIdempotencyKey(
                    userId, idempotencyKey, request
            );

            if (concurrent.isPresent()) {
                return responseForRepeatedRequest(concurrent.get());
            }

            if (ex instanceof CannotAcquireLockException) {
                throw new RequestInProgressException();
            }

            throw ex;
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
                String conflictCode = hotelConflictCode(error);

                boolean roomConflict =
                        "ROOM_OCCUPIED".equals(conflictCode)
                                || "ROOM_UNAVAILABLE".equals(conflictCode);

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

    public BookingResponse cancelBooking(
            Long bookingId,
            Long userId,
            boolean isAdmin
    ) {
        var candidate = sagaStateService.startCancellation(
                bookingId, userId, isAdmin
        );

        if (candidate.isEmpty()) {
            return getBookingById(bookingId, userId, isAdmin);
        }

        var booking = candidate.get();

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

        return sagaStateService.finishCancellation(bookingId);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<BookingResponse> getAllBookings(
            BookingStatus status,
            Pageable pageable
    ) {
        Pageable pageRequest = bookingPageable(pageable);

        Page<Booking> bookings = status == null
                ? bookingRepository.findAllByOrderByCreatedAtDesc(pageRequest)
                : bookingRepository.findByStatusOrderByCreatedAtDesc(
                status, pageRequest
        );

        return bookings.map(bookingMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getMyBookings(
            Long userId,
            Pageable pageable
    ) {
        return bookingRepository
                .findByUserIdOrderByCreatedAtDesc(
                        userId,
                        bookingPageable(pageable)
                )
                .map(bookingMapper::toResponse);
    }
    private BookingResponse responseForRepeatedRequest(
            BookingSagaStateService.ExistingCreation existing
    ) {
        BookingResponse booking = existing.booking();

        return switch (existing.outcome()) {case SUCCESS -> booking;

            case ROOM_CONFLICT -> throw new RoomAlreadyBookedException(booking.roomId());

            case NO_AVAILABLE_ROOMS -> throw new NoAvailableRoomsException();
            case ROOM_UNAVAILABLE ->
                    throw new RoomUnavailableException(booking.roomId());
            case PROCESSING, RECOVERING -> throw new RequestInProgressException();

            case FAILED -> throw new BookingSagaUnavailableException(
                    "Booking creation failed",
                    new IllegalStateException(
                            "Creation outcome: FAILED"
                    )
            );
        };
    }
    private BookingResponse compensateFailedCreation(
            BookingSagaStateService.SagaBooking pending,
            RuntimeException error,
            boolean autoSelect
    ) {
        String conflictCode = hotelConflictCode(error);

        boolean occupied = "ROOM_OCCUPIED".equals(conflictCode);
        boolean unavailable = "ROOM_UNAVAILABLE".equals(conflictCode);
        boolean roomConflict = occupied || unavailable;

        try {
            hotelServiceClient.compensateRoom(
                    pending.roomId(),
                    pending.reservationId()
            );

            BookingCreationOutcome outcome =
                    autoSelect && roomConflict
                            ? BookingCreationOutcome.NO_AVAILABLE_ROOMS
                            : occupied
                            ? BookingCreationOutcome.ROOM_CONFLICT
                            : unavailable
                            ? BookingCreationOutcome.ROOM_UNAVAILABLE
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

            if (occupied) {
                throw new RoomAlreadyBookedException(pending.roomId());
            }

            throw new RoomUnavailableException(pending.roomId());
        }

        throw new BookingSagaUnavailableException(
                "Booking cancelled because reservation failed",
                error
        );
    }
    private Pageable bookingPageable(Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            throw new IllegalArgumentException(
                    "Page size must not exceed 100"
            );
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "id")
        );
    }
    private String hotelConflictCode(RuntimeException error) {

        if (!(error instanceof RestClientResponseException httpError)
                || httpError.getStatusCode().value() != 409) {
            return null;
        }

        try {
            var json = HOTEL_ERROR_MAPPER.readTree(
                    httpError.getResponseBodyAsString()
            );

            return json == null
                    ? null
                    : json.path("code").asText(null);

        } catch (Exception ex) {
            log.warn("Cannot parse Hotel Service conflict response", ex);
            return null;
        }
    }
}