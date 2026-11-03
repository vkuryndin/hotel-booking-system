package com.hotelbooking.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.BookingNotFoundException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

class BookingQueryServiceTest {

  private final BookingRepository repository = mock(BookingRepository.class);
  private final BookingMapper mapper = mock(BookingMapper.class);
  private final BookingQueryService service = new BookingQueryService(repository, mapper);
  private final Pageable input = PageRequest.of(1, 20);

  private Booking booking() {
    return new Booking(7L, 12L, LocalDate.of(2055, 1, 2), LocalDate.of(2055, 1, 4));
  }

  private BookingResponse response() {
    return new BookingResponse(
        15L,
        7L,
        12L,
        LocalDate.of(2055, 1, 2),
        LocalDate.of(2055, 1, 4),
        BookingStatus.CONFIRMED,
        Instant.parse("2026-10-29T00:00:00Z"),
        1);
  }

  @Test
  void unknownBookingThrowsNotFound() {
    assertThrows(BookingNotFoundException.class, () -> service.getBookingById(15L, 7L, false));
  }

  @Test
  void anotherUsersBookingIsForbidden() {
    when(repository.findById(15L)).thenReturn(Optional.of(booking()));
    assertThrows(AccessDeniedException.class, () -> service.getBookingById(15L, 8L, false));
    verifyNoInteractions(mapper);
  }

  @Test
  void ownerCanReadBooking() {
    Booking booking = booking();
    BookingResponse expected = response();
    when(repository.findById(15L)).thenReturn(Optional.of(booking));
    when(mapper.toResponse(booking)).thenReturn(expected);
    assertSame(expected, service.getBookingById(15L, 7L, false));
  }

  @Test
  void adminCanReadAnotherUsersBooking() {
    Booking booking = booking();
    BookingResponse expected = response();
    when(repository.findById(15L)).thenReturn(Optional.of(booking));
    when(mapper.toResponse(booking)).thenReturn(expected);
    assertSame(expected, service.getBookingById(15L, 8L, true));
  }

  @Test
  void myBookingsUseUserFilterAndStableSort() {
    when(repository.findByUserIdOrderByCreatedAtDesc(any(Long.class), any(Pageable.class)))
        .thenReturn(Page.empty());
    assertEquals(0, service.getMyBookings(7L, input).getTotalElements());
    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findByUserIdOrderByCreatedAtDesc(eq(7L), page.capture());
    assertEquals(1, page.getValue().getPageNumber());
    assertEquals(20, page.getValue().getPageSize());
    assertEquals(Sort.Direction.DESC, page.getValue().getSort().getOrderFor("id").getDirection());
  }

  @Test
  void myBookingsFilterByStatus() {
    when(repository.findByUserIdAndStatusOrderByCreatedAtDesc(
            eq(7L), eq(BookingStatus.CANCELLED), any(Pageable.class)))
        .thenReturn(Page.empty());
    service.getMyBookings(7L, BookingStatus.CANCELLED, input);
    verify(repository)
        .findByUserIdAndStatusOrderByCreatedAtDesc(
            eq(7L), eq(BookingStatus.CANCELLED), any(Pageable.class));
  }

  @Test
  void checkoutFilterRequiresConfirmedStatus() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.getMyBookings(7L, BookingStatus.CANCELLED, LocalDate.of(2055, 1, 3), input));
    verifyNoInteractions(repository);
  }

  @Test
  void confirmedCheckoutFilterUsesDedicatedRepositoryQuery() {
    LocalDate from = LocalDate.of(2055, 1, 3);
    when(repository.findByUserIdAndStatusAndCheckOutGreaterThanEqualOrderByCreatedAtDesc(
            eq(7L), eq(BookingStatus.CONFIRMED), eq(from), any(Pageable.class)))
        .thenReturn(Page.empty());
    service.getMyBookings(7L, BookingStatus.CONFIRMED, from, input);
    verify(repository)
        .findByUserIdAndStatusAndCheckOutGreaterThanEqualOrderByCreatedAtDesc(
            eq(7L), eq(BookingStatus.CONFIRMED), eq(from), any(Pageable.class));
  }

  @Test
  void adminListWithoutStatusUsesAllRecords() {
    Booking booking = booking();
    BookingResponse expected = response();
    when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(booking)));
    when(mapper.toResponse(booking)).thenReturn(expected);
    assertSame(expected, service.getAllBookings(null, input).getContent().get(0));
  }

  @Test
  void adminListWithStatusUsesStatusFilter() {
    when(repository.findByStatusOrderByCreatedAtDesc(
            eq(BookingStatus.CONFIRMED), any(Pageable.class)))
        .thenReturn(Page.empty());
    service.getAllBookings(BookingStatus.CONFIRMED, input);
    verify(repository)
        .findByStatusOrderByCreatedAtDesc(eq(BookingStatus.CONFIRMED), any(Pageable.class));
  }

  @Test
  void maximumPageSizeIsEnforcedForBothUserAndAdmin() {
    Pageable tooLarge = PageRequest.of(0, 101);
    assertThrows(IllegalArgumentException.class, () -> service.getMyBookings(7L, tooLarge));
    assertThrows(IllegalArgumentException.class, () -> service.getAllBookings(null, tooLarge));
    verifyNoInteractions(repository);
  }
}
