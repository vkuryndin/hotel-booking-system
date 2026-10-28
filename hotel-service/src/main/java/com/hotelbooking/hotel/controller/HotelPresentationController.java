package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.request.HotelPresentationRequest;
import com.hotelbooking.hotel.dto.response.HotelResponse;
import com.hotelbooking.hotel.service.HotelPresentationService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hotels")
public class HotelPresentationController {

    private final HotelPresentationService service;

    public HotelPresentationController(
            HotelPresentationService service
    ) {
        this.service = service;
    }

    @PatchMapping("/{hotelId}/presentation")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelResponse update(
            @PathVariable Long hotelId,
            @Valid @RequestBody HotelPresentationRequest request
    ) {
        return service.update(hotelId, request);
    }
}