package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.response.HotelPhotoResponse;
import com.hotelbooking.hotel.service.HotelPhotoService;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Фотографии отелей")
@RestController
@RequestMapping("/api/hotels/{hotelId}/photos")
public class HotelPhotoController {

    private final HotelPhotoService service;

    public HotelPhotoController(HotelPhotoService service) {
        this.service = service;
    }

    @GetMapping
    public List<HotelPhotoResponse> list(
            @PathVariable Long hotelId
    ) {
        return service.list(hotelId);
    }

    @GetMapping("/{photoId}/file")
    public ResponseEntity<Resource> getFile(
            @PathVariable Long hotelId,
            @PathVariable Long photoId
    ) {
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                service.contentType(hotelId, photoId)
                        )
                )
                .body(service.getFile(hotelId, photoId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HotelPhotoResponse> upload(
            @PathVariable Long hotelId,
            @RequestPart("file") MultipartFile file
    ) {
        HotelPhotoResponse photo = service.upload(hotelId, file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(photo);
    }

    @PatchMapping("/{photoId}/primary")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelPhotoResponse setPrimary(
            @PathVariable Long hotelId,
            @PathVariable Long photoId
    ) {
        return service.setPrimary(hotelId, photoId);
    }

    @PatchMapping("/{photoId}/order")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelPhotoResponse setOrder(
            @PathVariable Long hotelId,
            @PathVariable Long photoId,
            @RequestParam int sortOrder
    ) {
        return service.setOrder(hotelId, photoId, sortOrder);
    }

    @DeleteMapping("/{photoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long hotelId,
            @PathVariable Long photoId
    ) {
        service.delete(hotelId, photoId);

        return ResponseEntity.noContent().build();
    }
}