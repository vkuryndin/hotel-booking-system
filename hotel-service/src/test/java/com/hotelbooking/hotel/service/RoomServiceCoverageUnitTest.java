package com.hotelbooking.hotel.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hotelbooking.hotel.dto.request.RoomRequest;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.exception.ResourceInUseException;
import com.hotelbooking.hotel.exception.RoomAlreadyExistsException;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.strategy.MostExpensiveStrategy;
import com.hotelbooking.hotel.strategy.RoomSelectionStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class RoomServiceCoverageUnitTest {

  private final RoomRepository rooms = mock(RoomRepository.class);
  private final HotelRepository hotels = mock(HotelRepository.class);
  private final RoomMapper mapper = mock(RoomMapper.class);
  private final RoomReservationRepository reservations = mock(RoomReservationRepository.class);
  private final RoomSelectionStrategy least = mock(RoomSelectionStrategy.class);
  private final MostExpensiveStrategy expensive = mock(MostExpensiveStrategy.class);
  private final RoomPhotoService photos = mock(RoomPhotoService.class);
  private final RoomService service =
      new RoomService(rooms, hotels, mapper, reservations, least, expensive, photos);

  @Test
  void createRoomRequiresExistingHotel() {
    assertThrows(
        HotelNotFoundException.class,
        () -> service.createRoom(new RoomRequest(19L, "1", null, 2, BigDecimal.TEN, Set.of())));
    verifyNoInteractions(rooms);
  }

  @Test
  void createRoomConvertsUniqueConstraint() {
    Hotel hotel = mock(Hotel.class);
    when(hotels.findByIdForUpdate(1L)).thenReturn(Optional.of(hotel));
    when(rooms.saveAndFlush(any(Room.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThrows(
        RoomAlreadyExistsException.class,
        () -> service.createRoom(new RoomRequest(1L, " 5 ", null, 2, BigDecimal.TEN, Set.of())));
  }

  @Test
  void missingRoomDelete() {
    assertThrows(RoomNotFoundException.class, () -> service.deleteRoom(23L));
    verifyNoInteractions(photos);
  }

  @Test
  void roomWithReservationCannotBeDeleted() {
    Room room = mock(Room.class);
    when(rooms.findByIdForUpdate(23L)).thenReturn(Optional.of(room));
    when(reservations.existsByRoom_Id(23L)).thenReturn(true);

    assertThrows(ResourceInUseException.class, () -> service.deleteRoom(23L));
    verifyNoInteractions(photos);
  }

  @Test
  void deleteRoomRemovesPhotosThenRow() {
    Room room = mock(Room.class);
    when(rooms.findByIdForUpdate(23L)).thenReturn(Optional.of(room));

    service.deleteRoom(23L);

    var order = inOrder(photos, rooms);
    order.verify(photos).removeAllForRoomDeletion(23L);
    order.verify(rooms).delete(room);
    order.verify(rooms).flush();
  }

  @Test
  void deleteDatabaseConflictIsResourceInUse() {
    Room room = mock(Room.class);
    when(rooms.findByIdForUpdate(23L)).thenReturn(Optional.of(room));
    doThrow(new DataIntegrityViolationException("fk")).when(rooms).flush();

    assertThrows(ResourceInUseException.class, () -> service.deleteRoom(23L));
  }

  @Test
  void recommendationRejectsInvalidHotel() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.recommendRooms(
                0L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), null, 1));
  }

  @Test
  void recommendationRejectsPastDate() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.recommendRooms(
                1L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(2), null, 1));
  }

  @Test
  void recommendationRejectsLongStay() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.recommendRooms(
                1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(367), null, 1));
  }

  @Test
  void recommendationRejectsZeroGuests() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.recommendRooms(
                1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), null, 0));
  }
}
