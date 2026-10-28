package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.request.HotelRequest;
import com.hotelbooking.hotel.dto.response.HotelResponse;
import com.hotelbooking.hotel.service.HotelService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import com.hotelbooking.hotel.entity.HotelAmenity;
import java.math.BigDecimal;
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
    @GetMapping
    public ResponseEntity<Page<HotelResponse>> getHotels(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) HotelAmenity amenity,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
                hotelService.searchHotels(
                        name,
                        address,
                        minRating,
                        amenity,
                        sortBy,
                        direction,
                        pageable
                )
        );
    }
    @GetMapping("/{id}")
    public ResponseEntity<HotelResponse> getHotelById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(hotelService.getHotelById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HotelResponse> updateHotel(
            @PathVariable Long id,
            @Valid @RequestBody HotelRequest request
    ) {
        return ResponseEntity.ok(
                hotelService.updateHotel(id, request)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteHotel(
            @PathVariable Long id
    ) {
        hotelService.deleteHotel(id);
        return ResponseEntity.noContent().build();
    }
}