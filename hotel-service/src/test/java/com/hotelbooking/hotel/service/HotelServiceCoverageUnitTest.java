package com.hotelbooking.hotel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hotelbooking.hotel.dto.request.HotelRequest;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.exception.HotelAlreadyExistsException;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.exception.ResourceInUseException;
import com.hotelbooking.hotel.mapper.HotelMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.strategy.RoomSelectionMode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HotelServiceCoverageUnitTest {

  private final HotelRepository hotels = mock(HotelRepository.class);
  private final RoomRepository rooms = mock(RoomRepository.class);
  private final HotelMapper mapper = mock(HotelMapper.class);
  private final HotelPhotoService photos = mock(HotelPhotoService.class);
  private final HotelService service = new HotelService(hotels, rooms, mapper, photos);

  @Test
  void missingHotelStrategy() {
    assertThrows(HotelNotFoundException.class, () -> service.getRoomSelectionStrategy(91L));
  }

  @Test
  void missingHotelStrategyUpdate() {
    assertThrows(
        HotelNotFoundException.class,
        () -> service.updateRoomSelectionStrategy(91L, RoomSelectionMode.MOST_EXPENSIVE));
  }

  @Test
  void getStrategyFromEntity() {
    Hotel hotel = mock(Hotel.class);
    when(hotels.findById(7L)).thenReturn(Optional.of(hotel));
    when(hotel.getRoomSelectionStrategy()).thenReturn(RoomSelectionMode.LEAST_BOOKED);

    assertEquals(RoomSelectionMode.LEAST_BOOKED, service.getRoomSelectionStrategy(7L));
  }

  @Test
  void updateStrategyLocksHotel() {
    Hotel hotel = mock(Hotel.class);
    when(hotels.findByIdForUpdate(7L)).thenReturn(Optional.of(hotel));
    when(hotel.getRoomSelectionStrategy()).thenReturn(RoomSelectionMode.MOST_EXPENSIVE);

    assertEquals(
        RoomSelectionMode.MOST_EXPENSIVE,
        service.updateRoomSelectionStrategy(7L, RoomSelectionMode.MOST_EXPENSIVE));
    verify(hotel).changeRoomSelectionStrategy(RoomSelectionMode.MOST_EXPENSIVE);
  }

  @Test
  void createDuplicateDoesNotSave() {
    when(hotels.existsByNameIgnoreCaseAndAddressIgnoreCase("North", "Main")).thenReturn(true);

    assertThrows(
        HotelAlreadyExistsException.class,
        () -> service.createHotel(new HotelRequest(" North ", " Main ")));
    verify(hotels, never()).saveAndFlush(any(Hotel.class));
  }

  @Test
  void createTrimsFields() {
    when(hotels.saveAndFlush(any(Hotel.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.createHotel(new HotelRequest(" North ", " Main "));

    verify(hotels).existsByNameIgnoreCaseAndAddressIgnoreCase("North", "Main");
    verify(mapper)
        .toResponse(
            argThat(hotel -> "North".equals(hotel.getName()) && "Main".equals(hotel.getAddress())));
  }

  @Test
  void updateMissingHotel() {
    assertThrows(
        HotelNotFoundException.class, () -> service.updateHotel(9L, new HotelRequest("N", "A")));
  }

  @Test
  void deleteMissingHotel() {
    assertThrows(HotelNotFoundException.class, () -> service.deleteHotel(9L));
    verifyNoInteractions(photos);
  }

  @Test
  void deleteHotelContainingRoomsDenied() {
    Hotel hotel = mock(Hotel.class);
    when(hotels.findByIdForUpdate(9L)).thenReturn(Optional.of(hotel));
    when(rooms.existsByHotelId(9L)).thenReturn(true);

    assertThrows(ResourceInUseException.class, () -> service.deleteHotel(9L));
    verifyNoInteractions(photos);
    verify(hotels, never()).delete(any(Hotel.class));
  }

  @Test
  void deleteEmptyHotelAlsoDeletesPhotoMetadata() {
    Hotel hotel = mock(Hotel.class);
    when(hotels.findByIdForUpdate(9L)).thenReturn(Optional.of(hotel));

    service.deleteHotel(9L);

    verify(photos).removeAllForHotelDeletion(9L);
    verify(hotels).delete(hotel);
    verify(hotels).flush();
  }

}
