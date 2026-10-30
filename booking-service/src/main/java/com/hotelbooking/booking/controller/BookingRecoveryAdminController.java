package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.response.ExhaustedRecoveryResponse;
import com.hotelbooking.booking.service.ManualBookingRecoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('ADMIN')")
public class BookingRecoveryAdminController {
    private final ManualBookingRecoveryService manualRecovery;

    public BookingRecoveryAdminController(ManualBookingRecoveryService manualRecovery) {
        this.manualRecovery = manualRecovery;
    }

    @GetMapping("/recovery/exhausted")
    public List<ExhaustedRecoveryResponse> exhausted() {
        return manualRecovery.exhausted();
    }

    @PostMapping("/{id}/recovery/retry")
    public ResponseEntity<ExhaustedRecoveryResponse> retry(@PathVariable Long id) {
        return ResponseEntity.accepted().body(manualRecovery.requeue(id));
    }
}
