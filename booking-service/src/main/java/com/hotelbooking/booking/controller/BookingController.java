package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import java.util.UUID;

import java.net.URI;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BookingRequest request,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        BookingResponse response =
                bookingService.createBooking(userId, request, idempotencyKey);

        return ResponseEntity
                .created(URI.create("/api/bookings/" + response.id()))
                .body(response);
    }

    @GetMapping("/my")
    public Page<BookingResponse> getMyBookings(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return bookingService.getMyBookings(userId, pageable);
    }
    @GetMapping("/{id}")
    public BookingResponse getBookingById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @CurrentSecurityContext(expression = "authentication")
            Authentication authentication
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return bookingService.getBookingById(
                id, userId, isAdmin(authentication)
        );
    }
    @PatchMapping("/{id}/cancel")
    public BookingResponse cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @CurrentSecurityContext(expression = "authentication")
            Authentication authentication
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return bookingService.cancelBooking(
                id, userId, isAdmin(authentication)
        );
    }

    @GetMapping
    public Page<BookingResponse> getAllBookings(
            @CurrentSecurityContext(expression = "authentication")
            Authentication authentication,
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return bookingService.getAllBookings(
                isAdmin(authentication), status, pageable
        );
    }
    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority())
                );
    }
}