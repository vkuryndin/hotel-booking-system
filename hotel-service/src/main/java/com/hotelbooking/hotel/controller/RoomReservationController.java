package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.request.RoomReservationReleaseRequest;
import com.hotelbooking.hotel.dto.request.RoomReservationRequest;
import com.hotelbooking.hotel.dto.response.RoomReservationResponse;
import com.hotelbooking.hotel.entity.RoomReservation;
import com.hotelbooking.hotel.service.RoomReservationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomReservationController {

    private final RoomReservationService reservationService;

    public RoomReservationController(RoomReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/{id}/confirm-availability")
    public ResponseEntity<RoomReservationResponse> confirmAvailability(
            @PathVariable Long id,
            @Valid @RequestBody RoomReservationRequest request
    ) {
        RoomReservation reservation = reservationService.reserve(
                request.reservationId(),
                id,
                request.checkIn(),
                request.checkOut(),
                request.guestCount()
        );

        RoomReservationResponse response = new RoomReservationResponse(
                reservation.getReservationId(),
                id,
                reservation.getCheckIn(),
                reservation.getCheckOut(),
                reservation.getStatus()
        );

        return ResponseEntity.ok(response);
    }
    @PostMapping("/{id}/release")
    public ResponseEntity<RoomReservationResponse> release(
            @PathVariable Long id,
            @Valid @RequestBody RoomReservationReleaseRequest request
    ) {
        RoomReservation reservation = reservationService.release(
                request.reservationId(),
                id,
                request.compensation()
        );

        return ResponseEntity.ok(
                new RoomReservationResponse(
                        reservation.getReservationId(),
                        id,
                        reservation.getCheckIn(),
                        reservation.getCheckOut(),
                        reservation.getStatus()
                )
        );
    }

}