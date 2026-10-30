package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.mapper.HotelMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.strategy.RoomSelectionMode;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HotelRoomSelectionStrategyTest {
    private final HotelRepository hotels = mock(HotelRepository.class);
    private final HotelService service = new HotelService(hotels, mock(RoomRepository.class),
            mock(HotelMapper.class), mock(HotelPhotoService.class));

    @Test void defaultsToLeastBooked() {
        Hotel hotel = new Hotel("Demo", "Address");
        when(hotels.findById(1L)).thenReturn(Optional.of(hotel));
        assertEquals(RoomSelectionMode.LEAST_BOOKED, service.getRoomSelectionStrategy(1L));
    }

    @Test void adminUpdatePersistsOnManagedEntity() {
        Hotel hotel = new Hotel("Demo", "Address");
        when(hotels.findByIdForUpdate(1L)).thenReturn(Optional.of(hotel));
        assertEquals(RoomSelectionMode.MOST_EXPENSIVE,
                service.updateRoomSelectionStrategy(1L, RoomSelectionMode.MOST_EXPENSIVE));
        assertEquals(RoomSelectionMode.MOST_EXPENSIVE, hotel.getRoomSelectionStrategy());
        verify(hotels).findByIdForUpdate(1L);
    }

    @Test void missingHotelFails() {
        when(hotels.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThrows(HotelNotFoundException.class,
                () -> service.updateRoomSelectionStrategy(7L, RoomSelectionMode.LEAST_BOOKED));
    }

    @Test void nullStrategyRejected() {
        Hotel hotel = new Hotel("Demo", "Address");
        when(hotels.findByIdForUpdate(1L)).thenReturn(Optional.of(hotel));
        assertThrows(NullPointerException.class,
                () -> service.updateRoomSelectionStrategy(1L, null));
    }
}
