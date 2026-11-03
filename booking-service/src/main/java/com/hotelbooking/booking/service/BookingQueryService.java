package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.BookingNotFoundException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingQueryService {

  private final BookingRepository bookingRepository;
  private final BookingMapper bookingMapper;

  public BookingQueryService(BookingRepository bookingRepository, BookingMapper bookingMapper) {
    this.bookingRepository = bookingRepository;
    this.bookingMapper = bookingMapper;
  }

  @Transactional(readOnly = true)
  public BookingResponse getBookingById(Long bookingId, Long userId, boolean isAdmin) {
    Booking booking =
        bookingRepository
            .findById(bookingId)
            .orElseThrow(() -> new BookingNotFoundException(bookingId));

    if (!isAdmin && !booking.getUserId().equals(userId)) {
      throw new AccessDeniedException("Access denied to booking: " + bookingId);
    }

    return bookingMapper.toResponse(booking);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public Page<BookingResponse> getAllBookings(BookingStatus status, Pageable pageable) {
    Pageable pageRequest = bookingPageable(pageable);

    Page<Booking> bookings =
        status == null
            ? bookingRepository.findAllByOrderByCreatedAtDesc(pageRequest)
            : bookingRepository.findByStatusOrderByCreatedAtDesc(status, pageRequest);

    return bookings.map(bookingMapper::toResponse);
  }

  @Transactional(readOnly = true)
  public Page<BookingResponse> getMyBookings(Long userId, Pageable pageable) {
    return getMyBookings(userId, null, pageable);
  }

  @Transactional(readOnly = true)
  public Page<BookingResponse> getMyBookings(Long userId, BookingStatus status, Pageable pageable) {
    return getMyBookings(userId, status, null, pageable);
  }

  @Transactional(readOnly = true)
  public Page<BookingResponse> getMyBookings(
      Long userId, BookingStatus status, LocalDate checkOutFrom, Pageable pageable) {
    if (checkOutFrom != null && status != BookingStatus.CONFIRMED) {
      throw new IllegalArgumentException("checkOutFrom requires CONFIRMED status");
    }
    Pageable pageRequest = bookingPageable(pageable);
    Page<Booking> bookings;
    if (checkOutFrom != null) {
      bookings =
          bookingRepository.findByUserIdAndStatusAndCheckOutGreaterThanEqualOrderByCreatedAtDesc(
              userId, status, checkOutFrom, pageRequest);
    } else if (status != null) {
      bookings =
          bookingRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageRequest);
    } else {
      bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
    }
    return bookings.map(bookingMapper::toResponse);
  }

  private Pageable bookingPageable(Pageable pageable) {
    if (pageable.getPageSize() > 100) {
      throw new IllegalArgumentException("Page size must not exceed 100");
    }

    return PageRequest.of(
        pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "id"));
  }
}
