package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.request.RoomRequest;
import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

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
}