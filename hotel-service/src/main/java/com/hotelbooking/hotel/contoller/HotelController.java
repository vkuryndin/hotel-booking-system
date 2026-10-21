package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.request.HotelRequest;
import com.hotelbooking.hotel.dto.response.HotelResponse;
import com.hotelbooking.hotel.service.HotelService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HotelResponse> createHotel(
            @Valid @RequestBody HotelRequest request) {

        HotelResponse response = hotelService.createHotel(request);

        URI location = URI.create("/api/hotels/" + response.id());

        return ResponseEntity.created(location).body(response);
    }
}