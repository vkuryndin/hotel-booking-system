package com.hotelbooking.booking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class BookingRecoveryAdminSecurityTest {
  @Test
  void adminOnlyGuardIsDeclaredForBothEndpoints() {
    var guard = BookingRecoveryAdminController.class.getAnnotation(PreAuthorize.class);
    assertNotNull(guard);
    assertEquals("hasRole('ADMIN')", guard.value());
  }
}
