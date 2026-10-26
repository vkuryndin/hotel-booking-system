package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.dto.client.HotelRoomResponse;
import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingNight;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.BookingNotFoundException;
import com.hotelbooking.booking.exception.InvalidBookingStatusException;
import com.hotelbooking.booking.exception.RoomAlreadyBookedException;
import com.hotelbooking.booking.exception.RoomUnavailableException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingNightRepository;
import com.hotelbooking.booking.repository.BookingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.hotelbooking.booking.entity.BookingStatus;

import com.hotelbooking.booking.exception.BookingSagaUnavailableException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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

        validateRoom(request.roomId());

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
    public BookingResponse createBooking(Long userId, BookingRequest request) {
        validateDates(request);
        validateRoom(request.roomId());

        var pending = sagaStateService.createPending(userId, request);

        try {
            var reservation = hotelServiceClient.reserveRoom(
                    pending.roomId(),
                    pending.reservationId(),
                    pending.checkIn(),
                    pending.checkOut()
            );

            if (!"RESERVED".equals(reservation.status())
                    || !pending.reservationId().equals(reservation.reservationId())
                    || !pending.roomId().equals(reservation.roomId())) {
                throw new IllegalStateException("Invalid Hotel Service response");
            }

            return sagaStateService.confirm(pending.bookingId());

        } catch (RuntimeException error) {

            try {
                hotelServiceClient.releaseRoom(
                        pending.roomId(),
                        pending.reservationId()
                );

                sagaStateService.cancel(pending.bookingId());

            } catch (RuntimeException compensationError) {
                log.error(
                        "Saga compensation failed for booking {}",
                        pending.bookingId(),
                        compensationError
                );

                error.addSuppressed(compensationError);

                throw new BookingSagaUnavailableException(
                        "Booking requires compensation: " + pending.bookingId(),
                        error
                );
            }

            if (error instanceof RestClientResponseException httpError
                    && httpError.getStatusCode().value() == 409) {
                throw new RoomAlreadyBookedException(pending.roomId());
            }

            throw new BookingSagaUnavailableException(
                    "Booking cancelled because reservation failed",
                    error
            );
        }
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
}