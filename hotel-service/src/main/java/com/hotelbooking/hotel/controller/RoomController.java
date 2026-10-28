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
import com.hotelbooking.hotel.entity.RoomAmenity;
import com.hotelbooking.hotel.entity.RoomCategory;
import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    public ResponseEntity<Page<RoomResponse>> getRooms(
            @RequestParam Long hotelId,
            @RequestParam(required = false) RoomCategory category,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) RoomAmenity amenity,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
                roomService.searchRooms(
                        hotelId,
                        category,
                        minCapacity,
                        minPrice,
                        maxPrice,
                        available,
                        amenity,
                        sortBy,
                        direction,
                        pageable
                )
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
    public ResponseEntity<Page<RoomResponse>> getAvailableRooms(
            @RequestParam Long hotelId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
                roomService.getAvailableRoomsByHotelId(hotelId, pageable)
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