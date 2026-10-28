package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.request.RoomAvailabilityRequest;
import com.hotelbooking.hotel.dto.request.RoomRequest;
import com.hotelbooking.hotel.dto.request.RoomUpdateRequest;
import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.service.RoomService;
import com.hotelbooking.hotel.strategy.RoomSelectionMode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody RoomRequest request
    ) {
        RoomResponse response = roomService.createRoom(request);

        return ResponseEntity
                .created(URI.create("/api/rooms/" + response.id()))
                .body(response);
    }
    @GetMapping
    public ResponseEntity<List<RoomResponse>> getRooms(
            @RequestParam Long hotelId
    ) {
        return ResponseEntity.ok(
                roomService.getRoomsByHotelId(hotelId)
        );
    }
    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoomById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }
    @PatchMapping("/{id}/availability")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> updateAvailability(
            @PathVariable Long id,
            @Valid @RequestBody RoomAvailabilityRequest request
    ) {
        return ResponseEntity.ok(
                roomService.updateAvailability(id, request)
        );
    }
    @GetMapping("/available")
    public ResponseEntity<List<RoomResponse>> getAvailableRooms(
            @RequestParam Long hotelId
    ) {
        return ResponseEntity.ok(
                roomService.getAvailableRoomsByHotelId(hotelId)
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> updateRoom(
            @PathVariable Long id,
            @Valid @RequestBody RoomUpdateRequest request
    ) {
        return ResponseEntity.ok(roomService.updateRoom(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable Long id
    ) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/recommend")
    public ResponseEntity<List<RoomResponse>> recommendRooms(
            @RequestParam Long hotelId,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate checkIn,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate checkOut,

            @RequestParam(defaultValue = "LEAST_BOOKED")
            RoomSelectionMode strategy,

            @RequestParam(defaultValue = "1")
            int guestCount
    ) {
        return ResponseEntity.ok(
                roomService.recommendRooms(
                        hotelId,
                        checkIn,
                        checkOut,
                        strategy,
                        guestCount
                )
        );
    }
}