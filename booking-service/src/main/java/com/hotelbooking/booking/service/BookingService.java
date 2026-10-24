package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.dto.client.HotelRoomResponse;
import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingNight;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.RoomAlreadyBookedException;
import com.hotelbooking.booking.exception.RoomUnavailableException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingNightRepository;
import com.hotelbooking.booking.repository.BookingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookingService {

    private final HotelServiceClient hotelServiceClient;

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final BookingNightRepository bookingNightRepository;

    public BookingService(
            HotelServiceClient hotelServiceClient,
            BookingRepository bookingRepository,
            BookingNightRepository bookingNightRepository,
            BookingMapper bookingMapper
    ) {
        this.hotelServiceClient = hotelServiceClient;
        this.bookingRepository = bookingRepository;
        this.bookingNightRepository = bookingNightRepository;
        this.bookingMapper = bookingMapper;
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
    @Transactional
    public BookingResponse createBooking(Long userId, BookingRequest request) {

        validateBooking(request);

        Booking booking = new Booking(
                userId,
                request.roomId(),
                request.checkIn(),
                request.checkOut()
        );

        Booking saved = bookingRepository.saveAndFlush(booking);

        List<BookingNight> nights = request.checkIn()
                .datesUntil(request.checkOut())
                .map(date -> new BookingNight(saved, date))
                .toList();

        try {
            bookingNightRepository.saveAllAndFlush(nights);
        } catch (DataIntegrityViolationException ex) {
            throw new RoomAlreadyBookedException(request.roomId());
        }

        return bookingMapper.toResponse(saved);
    }
}