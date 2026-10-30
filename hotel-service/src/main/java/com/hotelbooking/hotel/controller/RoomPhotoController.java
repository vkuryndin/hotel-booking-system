package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.response.RoomPhotoResponse;
import com.hotelbooking.hotel.service.RoomPhotoService;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/rooms/{roomId}/photos")
public class RoomPhotoController {

    private final RoomPhotoService service;

    public RoomPhotoController(RoomPhotoService service) {
        this.service = service;
    }

    @GetMapping
    public List<RoomPhotoResponse> list(
            @PathVariable Long roomId
    ) {
        return service.list(roomId);
    }

    @GetMapping("/{photoId}/file")
    public ResponseEntity<Resource> getFile(
            @PathVariable Long roomId,
            @PathVariable Long photoId
    ) {
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                service.contentType(roomId, photoId)
                        )
                )
                .body(service.getFile(roomId, photoId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomPhotoResponse> upload(
            @PathVariable Long roomId,
            @RequestPart("file") MultipartFile file
    ) {
        RoomPhotoResponse photo = service.upload(roomId, file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(photo);
    }

    @PatchMapping("/{photoId}/primary")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomPhotoResponse setPrimary(
            @PathVariable Long roomId,
            @PathVariable Long photoId
    ) {
        return service.setPrimary(roomId, photoId);
    }

    @PatchMapping("/{photoId}/order")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomPhotoResponse setOrder(
            @PathVariable Long roomId,
            @PathVariable Long photoId,
            @RequestParam int sortOrder
    ) {
        return service.setOrder(roomId, photoId, sortOrder);
    }

    @DeleteMapping("/{photoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long roomId,
            @PathVariable Long photoId
    ) {
        service.delete(roomId, photoId);

        return ResponseEntity.noContent().build();
    }
}