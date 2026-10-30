package com.hotelbooking.booking.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.junit.jupiter.api.Assertions.*;

class BookingRecoveryAdminSecurityTest {
    @Test
    void adminOnlyGuardIsDeclaredForBothEndpoints() {
        var guard = BookingRecoveryAdminController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(guard);
        assertEquals("hasRole('ADMIN')", guard.value());
    }
}
