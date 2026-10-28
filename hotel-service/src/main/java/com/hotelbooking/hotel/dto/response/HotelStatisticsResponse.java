package com.hotelbooking.hotel.dto.response;

import java.time.LocalDate;

public record HotelStatisticsResponse(
        Long hotelId,
        LocalDate statisticsDate,
        long totalRooms,
        long availableRooms,
        long unavailableRooms,
        long occupiedRoomsToday,
        long totalReservations,
        long reservedReservations,
        long releasedReservations,
        long totalTimesBooked
) {
}