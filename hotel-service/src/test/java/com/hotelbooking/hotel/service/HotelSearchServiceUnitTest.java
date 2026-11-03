package com.hotelbooking.hotel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hotelbooking.hotel.dto.response.HotelResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.HotelAmenity;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.mapper.HotelMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

class HotelSearchServiceUnitTest {

  private final HotelRepository hotels = mock(HotelRepository.class);
  private final HotelMapper mapper = mock(HotelMapper.class);
  private final HotelSearchService service = new HotelSearchService(hotels, mapper);

  @Test
  void missingHotelById() {
    assertThrows(HotelNotFoundException.class, () -> service.getHotelById(91L));
  }

  @Test
  void searchRejectsLargePage() {
    assertThrows(IllegalArgumentException.class, () -> search(null, "id", "asc", 101));
    verifyNoInteractions(hotels);
  }

  @Test
  void searchRejectsNegativeRating() {
    assertThrows(
        IllegalArgumentException.class, () -> search(new BigDecimal("-0.1"), "id", "asc", 10));
  }

  @Test
  void searchRejectsRatingAboveFive() {
    assertThrows(
        IllegalArgumentException.class, () -> search(new BigDecimal("5.1"), "id", "asc", 10));
  }

  @Test
  void searchRejectsUnknownSort() {
    assertThrows(IllegalArgumentException.class, () -> search(null, "password", "asc", 10));
  }

  @Test
  void searchRejectsUnknownDirection() {
    assertThrows(IllegalArgumentException.class, () -> search(null, "id", "sideways", 10));
  }

  @Test
  void searchAcceptsValidFilters() {
    when(hotels.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

    assertTrue(search(new BigDecimal("3.5"), "rating", "DESC", 10).isEmpty());

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(hotels).findAll(any(Specification.class), pageableCaptor.capture());

    Sort.Order ratingSort = pageableCaptor.getValue().getSort().getOrderFor("rating");
    assertNotNull(ratingSort);
    assertEquals(Sort.Direction.DESC, ratingSort.getDirection());
  }

  private Page<?> search(BigDecimal rating, String sort, String direction, int pageSize) {
    return service.searchHotels(
        "test", "city", rating, HotelAmenity.WIFI, sort, direction, PageRequest.of(0, pageSize));
  }

  @Test
  void getHotelByIdMapsLoadedHotel() {
    Hotel hotel = mock(Hotel.class);
    HotelResponse response = new HotelResponse(7L, "North", "Main", null, null, null, null, null);
    when(hotels.findById(7L)).thenReturn(Optional.of(hotel));
    when(mapper.toResponse(hotel)).thenReturn(response);

    assertSame(response, service.getHotelById(7L));
    verify(hotels).findById(7L);
    verify(mapper).toResponse(hotel);
  }

  @Test
  void searchAcceptsTextQueryAndMultipleAmenities() {
    when(hotels.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(Page.empty());

    assertTrue(
        service
            .searchHotels(
                null,
                null,
                null,
                null,
                "name",
                "ASC",
                PageRequest.of(1, 10),
                "hotel_%",
                List.of(HotelAmenity.WIFI, HotelAmenity.POOL))
            .isEmpty());

    ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(hotels).findAll(any(Specification.class), pageCaptor.capture());
    assertEquals(1, pageCaptor.getValue().getPageNumber());
    assertEquals(10, pageCaptor.getValue().getPageSize());
    assertEquals(
        Sort.Direction.ASC, pageCaptor.getValue().getSort().getOrderFor("name").getDirection());
    assertNotNull(pageCaptor.getValue().getSort().getOrderFor("id"));
  }

  @Test
  void searchReadMethodsRetainReadonlyTransaction() throws NoSuchMethodException {
    assertTrue(
        HotelSearchService.class
            .getMethod("getHotelById", Long.class)
            .getAnnotation(Transactional.class)
            .readOnly());
    assertTrue(
        HotelSearchService.class
            .getMethod(
                "searchHotels",
                String.class,
                String.class,
                BigDecimal.class,
                HotelAmenity.class,
                String.class,
                String.class,
                Pageable.class)
            .getAnnotation(Transactional.class)
            .readOnly());
    assertTrue(
        HotelSearchService.class
            .getMethod(
                "searchHotels",
                String.class,
                String.class,
                BigDecimal.class,
                HotelAmenity.class,
                String.class,
                String.class,
                Pageable.class,
                String.class,
                List.class)
            .getAnnotation(Transactional.class)
            .readOnly());
  }
}
