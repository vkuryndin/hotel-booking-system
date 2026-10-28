package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.response.HotelPhotoResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.HotelPhoto;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.repository.HotelPhotoRepository;
import com.hotelbooking.hotel.repository.HotelRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class HotelPhotoService {

    private final HotelRepository hotelRepository;
    private final HotelPhotoRepository photoRepository;
    private final Path storageRoot;

    public HotelPhotoService(
            HotelRepository hotelRepository,
            HotelPhotoRepository photoRepository,
            @Value("${app.photos.directory:storage/hotels}")
            String directory
    ) {
        this.hotelRepository = hotelRepository;
        this.photoRepository = photoRepository;
        this.storageRoot = Path.of(directory)
                .toAbsolutePath()
                .normalize();
    }

    @Transactional(readOnly = true)
    public List<HotelPhotoResponse> list(Long hotelId) {
        findHotel(hotelId);

        return photoRepository
                .findByHotel_IdOrderBySortOrderAscIdAsc(hotelId)
                .stream()
                .map(HotelPhotoResponse::from)
                .toList();
    }

    @Transactional
    public HotelPhotoResponse upload(
            Long hotelId,
            MultipartFile file
    ) {
        Hotel hotel = findHotel(hotelId);

        if (file == null
                || file.isEmpty()
                || file.getSize() > 5L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "Photo must be 1 byte to 5 MB"
            );
        }

        List<HotelPhoto> existing = photoRepository
                .findByHotel_IdOrderBySortOrderAscIdAsc(hotelId);

        if (existing.size() >= 10) {
            throw new IllegalArgumentException(
                    "Maximum 10 photos per hotel"
            );
        }

        String contentType;

        try (InputStream input = file.getInputStream()) {
            contentType = detectType(input.readNBytes(12));
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Cannot read photo", ex
            );
        }

        if (contentType == null) {
            throw new IllegalArgumentException(
                    "Only valid JPEG, PNG, WebP photos are allowed"
            );
        }

        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".webp";
        };

        String fileName = UUID.randomUUID() + extension;

        Path destination = hotelDirectory(hotelId)
                .resolve(fileName);

        try {
            Files.createDirectories(destination.getParent());

            try (InputStream input = file.getInputStream()) {
                Files.copy(input, destination);
            }
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Cannot store photo", ex
            );
        }

        try {
            int sortOrder = existing.stream()
                    .mapToInt(HotelPhoto::getSortOrder)
                    .max()
                    .orElse(-1) + 1;

            HotelPhoto photo = new HotelPhoto(
                    hotel,
                    fileName,
                    contentType,
                    existing.isEmpty(),
                    sortOrder
            );

            HotelPhoto saved = photoRepository.saveAndFlush(photo);

            return HotelPhotoResponse.from(saved);

        } catch (RuntimeException ex) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Resource getFile(
            Long hotelId,
            Long photoId
    ) {
        HotelPhoto photo = findPhoto(hotelId, photoId);

        Path directory = hotelDirectory(hotelId);

        Path file = directory
                .resolve(photo.getFileName())
                .normalize();

        if (!file.startsWith(directory)) {
            throw new IllegalArgumentException(
                    "Invalid photo path"
            );
        }

        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Photo file not found"
            );
        }

        return new FileSystemResource(file);
    }

    @Transactional(readOnly = true)
    public String contentType(
            Long hotelId,
            Long photoId
    ) {
        return findPhoto(hotelId, photoId).getContentType();
    }

    @Transactional
    public HotelPhotoResponse setPrimary(
            Long hotelId,
            Long photoId
    ) {
        HotelPhoto selected = findPhoto(hotelId, photoId);

        List<HotelPhoto> photos = photoRepository
                .findByHotel_IdOrderBySortOrderAscIdAsc(hotelId);

        for (HotelPhoto photo : photos) {
            photo.setPrimaryPhoto(
                    photo.getId().equals(selected.getId())
            );
        }

        return HotelPhotoResponse.from(selected);
    }

    @Transactional
    public HotelPhotoResponse setOrder(
            Long hotelId,
            Long photoId,
            int sortOrder
    ) {
        if (sortOrder < 0) {
            throw new IllegalArgumentException(
                    "sortOrder must be >= 0"
            );
        }

        HotelPhoto photo = findPhoto(hotelId, photoId);
        photo.setSortOrder(sortOrder);

        return HotelPhotoResponse.from(photo);
    }

    @Transactional
    public void delete(
            Long hotelId,
            Long photoId
    ) {
        HotelPhoto photo = findPhoto(hotelId, photoId);

        boolean wasPrimary = photo.isPrimaryPhoto();

        photoRepository.delete(photo);
        photoRepository.flush();

        if (wasPrimary) {
            List<HotelPhoto> remaining = photoRepository
                    .findByHotel_IdOrderBySortOrderAscIdAsc(hotelId);

            if (!remaining.isEmpty()) {
                remaining.get(0).setPrimaryPhoto(true);
            }
        }

        try {
            Files.deleteIfExists(
                    hotelDirectory(hotelId)
                            .resolve(photo.getFileName())
            );
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Cannot delete photo file", ex
            );
        }
    }

    private Hotel findHotel(Long hotelId) {
        return hotelRepository.findById(hotelId)
                .orElseThrow(
                        () -> new HotelNotFoundException(hotelId)
                );
    }

    private HotelPhoto findPhoto(
            Long hotelId,
            Long photoId
    ) {
        return photoRepository.findById(photoId)
                .filter(
                        photo -> photo.getHotel()
                                .getId()
                                .equals(hotelId)
                )
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Photo not found"
                        )
                );
    }

    private Path hotelDirectory(Long hotelId) {
        return storageRoot
                .resolve(String.valueOf(hotelId))
                .normalize();
    }

    private String detectType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 255) == 255
                && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255) {
            return "image/jpeg";
        }

        if (bytes.length >= 8
                && Arrays.equals(
                Arrays.copyOf(bytes, 8),
                new byte[] {
                        (byte) 137, 80, 78, 71,
                        13, 10, 26, 10
                }
        )) {
            return "image/png";
        }

        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "image/webp";
        }

        return null;
    }
}