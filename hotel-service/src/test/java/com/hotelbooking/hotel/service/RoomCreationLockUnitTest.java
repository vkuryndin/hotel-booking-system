package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.request.RoomRequest;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.exception.RoomAlreadyExistsException;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.strategy.MostExpensiveStrategy;
import com.hotelbooking.hotel.strategy.RoomSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoomCreationLockUnitTest {
    private RoomRepository rooms;
    private HotelRepository hotels;
    private RoomMapper mapper;
    private RoomService service;

    @BeforeEach
    void setUp() {
        rooms = mock(RoomRepository.class);
        hotels = mock(HotelRepository.class);
        mapper = mock(RoomMapper.class);
        service = new RoomService(rooms, mock(RoomPhotoService.class), hotels, mapper,
                mock(RoomReservationRepository.class),
                mock(RoomSelectionStrategy.class),
                mock(MostExpensiveStrategy.class));
    }

    @Test
    void creationLocksHotelRowBeforeSavingRoom() {
        Hotel hotel = new Hotel("Example", "Street");
        when(hotels.findByIdForUpdate(17L)).thenReturn(Optional.of(hotel));
        when(rooms.saveAndFlush(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createRoom(request());

        var order = inOrder(hotels, rooms);
        order.verify(hotels).findByIdForUpdate(17L);
        order.verify(rooms).saveAndFlush(any(Room.class));
        verify(hotels, never()).findById(any());
        verify(mapper).toResponse(any(Room.class));
    }

    @Test
    void missingHotelDoesNotWriteRoom() {
        when(hotels.findByIdForUpdate(17L)).thenReturn(Optional.empty());

        assertThrows(HotelNotFoundException.class, () -> service.createRoom(request()));

        verifyNoInteractions(rooms, mapper);
        verify(hotels).findByIdForUpdate(17L);
    }

    @Test
    void duplicateRoomStillUsesExistingConflictHandling() {
        when(hotels.findByIdForUpdate(17L))
                .thenReturn(Optional.of(new Hotel("Example", "Street")));
        when(rooms.saveAndFlush(any(Room.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate number"));

        assertThrows(RoomAlreadyExistsException.class, () -> service.createRoom(request()));
        verify(hotels).findByIdForUpdate(17L);
    }

    private RoomRequest request() {
        return new RoomRequest(17L, " 101 ", null, null, null, null);
    }
}
