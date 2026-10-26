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
    public SagaBooking createPending(Long userId, BookingRequest request) {
        Booking booking = bookingRepository.saveAndFlush(
                new Booking(
                        userId,
                        request.roomId(),
                        request.checkIn(),
                        request.checkOut()
                )
        );

        return snapshot(booking);
    }

    @Transactional
    public BookingResponse confirm(Long bookingId) {
        Booking booking = find(bookingId);

        try {
            booking.confirm();
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
}