package com.hotelbooking.hotel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

class RoomSearchServiceCoverageUnitTest {

  private final RoomRepository rooms = mock(RoomRepository.class);
  private final HotelRepository hotels = mock(HotelRepository.class);
  private final RoomMapper mapper = mock(RoomMapper.class);
  private final RoomSearchService service = new RoomSearchService(rooms, hotels, mapper);

  @Test
  void missingRoomGet() {
    assertThrows(RoomNotFoundException.class, () -> service.getRoomById(23L));
  }

  @Test
  void roomSearchRejectsHugePage() {
    assertThrows(
        IllegalArgumentException.class, () -> search(1L, null, null, null, "id", "asc", 101));
  }

  @Test
  void roomSearchRequiresHotelId() {
    assertThrows(
        IllegalArgumentException.class, () -> search(null, null, null, null, "id", "asc", 10));
  }

  @Test
  void roomSearchRequiresExistingHotel() {
    assertThrows(HotelNotFoundException.class, () -> search(1L, null, null, null, "id", "asc", 10));
  }

  @Test
  void roomSearchRejectsInvalidCapacity() {
    when(hotels.existsById(1L)).thenReturn(true);
    assertThrows(IllegalArgumentException.class, () -> search(1L, 0, null, null, "id", "asc", 10));
  }

  @Test
  void roomSearchRejectsNegativeMinPrice() {
    when(hotels.existsById(1L)).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () -> search(1L, null, BigDecimal.ONE.negate(), null, "id", "asc", 10));
  }

  @Test
  void roomSearchRejectsNegativeMaxPrice() {
    when(hotels.existsById(1L)).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () -> search(1L, null, null, BigDecimal.ONE.negate(), "id", "asc", 10));
  }

  @Test
  void roomSearchRejectsInvertedPriceRange() {
    when(hotels.existsById(1L)).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () -> search(1L, null, BigDecimal.TEN, BigDecimal.ONE, "id", "asc", 10));
  }

  @Test
  void roomSearchRejectsUnknownSort() {
    when(hotels.existsById(1L)).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () -> search(1L, null, null, null, "hotel.password", "asc", 10));
  }

  @Test
  void roomSearchRejectsBadDirection() {
    when(hotels.existsById(1L)).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class, () -> search(1L, null, null, null, "id", "down", 10));
  }

  @Test
  void roomSearchUsesPaginationAndSort() {
    when(hotels.existsById(1L)).thenReturn(true);
    when(rooms.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

    assertTrue(search(1L, 2, null, null, "number", "DESC", 10).isEmpty());

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(rooms).findAll(any(Specification.class), pageableCaptor.capture());

    Sort.Order numberSort = pageableCaptor.getValue().getSort().getOrderFor("number");
    assertNotNull(numberSort);
    assertEquals(Sort.Direction.DESC, numberSort.getDirection());
  }

  private Page<?> search(
      Long hotelId,
      Integer capacity,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      String sort,
      String direction,
      int pageSize) {
    return service.searchRooms(
        hotelId,
        null,
        capacity,
        minPrice,
        maxPrice,
        null,
        null,
        sort,
        direction,
        PageRequest.of(0, pageSize));
  }
}
