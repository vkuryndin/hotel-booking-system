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
    private RoomPhotoService photos;

    @BeforeEach
    void setUp() {
        rooms = mock(RoomRepository.class);
        hotels = mock(HotelRepository.class);
        mapper = mock(RoomMapper.class);
        photos = mock(RoomPhotoService.class);
        service = new RoomService(rooms, hotels, mapper,
                mock(RoomReservationRepository.class),
                mock(RoomSelectionStrategy.class),
                mock(MostExpensiveStrategy.class), photos);
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

    @Test
    void deletingRoomWithPhotosRemovesMetadataBeforeRoomDeletion() {
        var reservations = mock(RoomReservationRepository.class);
        var local = new RoomService(rooms, hotels, mapper, reservations,
                mock(RoomSelectionStrategy.class), mock(MostExpensiveStrategy.class), photos);
        when(rooms.findByIdForUpdate(9L)).thenReturn(Optional.of(new Room(new Hotel("Example", "Street"), "101")));
        when(reservations.existsByRoom_Id(9L)).thenReturn(false);
        local.deleteRoom(9L);
        var order = inOrder(photos, rooms);
        order.verify(photos).removeAllForRoomDeletion(9L);
        order.verify(rooms).delete(any(Room.class));
        order.verify(rooms).flush();
    }

    private RoomRequest request() {
        return new RoomRequest(17L, " 101 ", null, null, null, null);
    }
}
