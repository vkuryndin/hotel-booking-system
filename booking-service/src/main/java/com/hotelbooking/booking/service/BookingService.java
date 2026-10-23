package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.dto.client.HotelRoomResponse;
import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.exception.RoomUnavailableException;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

    private final HotelServiceClient hotelServiceClient;

    public BookingService(HotelServiceClient hotelServiceClient) {
        this.hotelServiceClient = hotelServiceClient;
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
}