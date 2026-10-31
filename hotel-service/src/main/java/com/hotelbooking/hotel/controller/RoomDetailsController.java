package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.request.RoomDetailsRequest;
import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.service.RoomDetailsService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Характеристики номеров")
@RestController
@RequestMapping("/api/rooms")
public class RoomDetailsController {

    private final RoomDetailsService service;

    public RoomDetailsController(RoomDetailsService service) {
        this.service = service;
    }

    @PatchMapping("/{roomId}/details")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse update(
            @PathVariable Long roomId,
            @Valid @RequestBody RoomDetailsRequest request
    ) {
        return service.update(roomId, request);
    }
}