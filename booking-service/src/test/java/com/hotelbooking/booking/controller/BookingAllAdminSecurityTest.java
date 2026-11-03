package com.hotelbooking.booking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.service.BookingQueryService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

class BookingAllAdminSecurityTest {

  @Test
  void allBookingsEndpointRequiresAdminRole() throws Exception {
    Method method =
        BookingController.class.getMethod("getAllBookings", BookingStatus.class, Pageable.class);
    PreAuthorize guard = method.getAnnotation(PreAuthorize.class);
    assertNotNull(guard);
    assertEquals("hasRole('ADMIN')", guard.value());
  }

  @Test
  void allBookingsServiceAlsoRequiresAdminRole() throws Exception {
    Method method =
        BookingQueryService.class.getMethod("getAllBookings", BookingStatus.class, Pageable.class);
    PreAuthorize guard = method.getAnnotation(PreAuthorize.class);
    assertNotNull(guard);
    assertEquals("hasRole('ADMIN')", guard.value());
  }
}
