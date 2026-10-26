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

import java.net.URI;
import java.util.List;

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
            @Valid @RequestBody BookingRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        BookingResponse response =
                bookingService.createBooking(userId, request);

        return ResponseEntity
                .created(URI.create("/api/bookings/" + response.id()))
                .body(response);
    }
    @GetMapping("/my")
    public List<BookingResponse> getMyBookings(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return bookingService.getMyBookings(userId);
    }
    @GetMapping("/{id}")
    public BookingResponse getBookingById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        boolean isAdmin = "ADMIN".equals(jwt.getClaimAsString("role"));

        return bookingService.getBookingById(id, userId, isAdmin);
    }

    @PatchMapping("/{id}/cancel")
    public BookingResponse cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        boolean isAdmin = "ADMIN".equals(jwt.getClaimAsString("role"));

        return bookingService.cancelBooking(id, userId, isAdmin);
    }

    @GetMapping
    public Page<BookingResponse> getAllBookings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        boolean isAdmin = "ADMIN".equals(jwt.getClaimAsString("role"));

        return bookingService.getAllBookings(isAdmin, status, pageable);
    }
}