package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.*;
import com.hotelbooking.hotel.strategy.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;

class RoomSelectionPerHotelTest {
    @Test void missingOverrideUsesHotelStrategy() {
        var rooms = mock(RoomRepository.class);
        var hotels = mock(HotelRepository.class);
        var least = mock(RoomSelectionStrategy.class);
        var expensive = mock(MostExpensiveStrategy.class);
        var service = new RoomService(rooms, hotels, mock(RoomMapper.class),
                mock(RoomReservationRepository.class), least, expensive, mock(RoomPhotoService.class));
        var hotel = new Hotel("Demo", "Address");
        hotel.changeRoomSelectionStrategy(RoomSelectionMode.MOST_EXPENSIVE);
        when(hotels.findById(3L)).thenReturn(Optional.of(hotel));
        var checkIn = LocalDate.now().plusDays(1);
        var checkOut = checkIn.plusDays(1);
        when(rooms.findAvailableForPeriod(3L, checkIn, checkOut)).thenReturn(List.of());
        when(expensive.rank(List.of())).thenReturn(List.of());
        when(least.rank(List.of())).thenReturn(List.of());

        service.recommendRooms(3L, checkIn, checkOut, null, 1);
        verify(expensive).rank(List.of());
        service.recommendRooms(3L, checkIn, checkOut, RoomSelectionMode.LEAST_BOOKED, 1);
        verify(least).rank(List.of());
    }
}
