package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.response.HotelPhotoResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.HotelPhoto;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.repository.HotelPhotoRepository;
import com.hotelbooking.hotel.repository.HotelRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.hotelbooking.hotel.exception.PhotoNotFoundException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class HotelPhotoService {

    private static final Logger log =
            LoggerFactory.getLogger(HotelPhotoService.class);

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
        // №17: сериализуем изменения фотографий одного отеля.
        Hotel hotel = lockHotel(hotelId);

        if (file == null
                || file.isEmpty()
                || file.getSize() > 5L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "Photo must be 1 byte to 5 MB"
            );
        }

        // Проверяем лимит уже под блокировкой отеля.
        List<HotelPhoto> existing = photoRepository
                .findByHotel_IdOrderBySortOrderAscIdAsc(hotelId);

        if (existing.size() >= 10) {
            throw new IllegalArgumentException(
                    "Maximum 10 photos per hotel"
            );
        }

        String contentType;

        try (InputStream input = file.getInputStream()) {
            contentType = PhotoFileOperations.detectType(input.readNBytes(12));
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

        // A file with a valid signature may still be truncated or corrupted.
        // Decode the complete image BEFORE writing it to storage or the database.
        PhotoFileOperations.validateDecodedImage(file, contentType);

        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".webp";
        };

        String fileName = UUID.randomUUID() + extension;

        Path destination = hotelDirectory(hotelId)
                .resolve(fileName);

        // №17: если БД откатится, удаляем созданный файл.
        // Регистрируем ДО копирования, чтобы убрать даже частично
        // записанный файл при ошибке Files.copy().
        PhotoFileOperations.registerRollbackCleanup(destination, log);

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

        // Порядок и primary рассчитываются под блокировкой.
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

        // Materialize the small (max 5 MB) photo before returning the resource.
        // Streaming a filesystem resource would race with deletion after transaction end.
        try {
            return new ByteArrayResource(Files.readAllBytes(file));
        } catch (java.nio.file.NoSuchFileException ex) {
            throw new PhotoNotFoundException("Photo file not found");
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read photo file", ex);
        }
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
        // №17: два параллельных PATCH не смогут назначить
        // две главные фотографии одновременно.
        lockHotel(hotelId);

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

        // №17: изменение порядка также сериализуем.
        lockHotel(hotelId);

        HotelPhoto photo = findPhoto(hotelId, photoId);
        photo.setSortOrder(sortOrder);

        return HotelPhotoResponse.from(photo);
    }

    @Transactional
    public void delete(
            Long hotelId,
            Long photoId
    ) {
        // №17: защищаем удаление и выбор новой primary.
        lockHotel(hotelId);

        HotelPhoto photo = findPhoto(hotelId, photoId);

        boolean wasPrimary = photo.isPrimaryPhoto();

        Path directory = hotelDirectory(hotelId);
        Path file = directory
                .resolve(photo.getFileName())
                .normalize();

        if (!file.startsWith(directory)) {
            throw new IllegalArgumentException(
                    "Invalid photo path"
            );
        }

        // Сначала удаляем только метаданные в БД.
        photoRepository.delete(photo);
        photoRepository.flush();

        if (wasPrimary) {
            List<HotelPhoto> remaining = photoRepository
                    .findByHotel_IdOrderBySortOrderAscIdAsc(hotelId);

            if (!remaining.isEmpty()) {
                remaining.get(0).setPrimaryPhoto(true);
            }
        }

        // №17: физический файл удалится только после
        // успешного COMMIT транзакции.
        PhotoFileOperations.registerDeleteAfterCommit(file, log);
    }

    /**
     * Вызывается только из HotelService.deleteHotel() внутри общей транзакции.
     * Удаляем строки из БД; файлы останутся до COMMIT.
     * Блокировка отеля уже удерживается вызывающим методом.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeAllForHotelDeletion(Long hotelId) {
        List<HotelPhoto> photos = photoRepository
                .findByHotel_IdOrderBySortOrderAscIdAsc(hotelId);

        photoRepository.deleteAll(photos);
        photoRepository.flush();

        // Чистим весь каталог данного отеля, включая файлы,
        // оставшиеся от предыдущих неуспешных операций.
        PhotoFileOperations.registerDirectoryDeletionAfterCommit(hotelDirectory(hotelId), log);
    }

    private Hotel findHotel(Long hotelId) {
        return hotelRepository.findById(hotelId)
                .orElseThrow(
                        () -> new HotelNotFoundException(hotelId)
                );
    }

    // №17: единая блокировка для всех операций изменения фото.
    private Hotel lockHotel(Long hotelId) {
        return hotelRepository.findByIdForUpdate(hotelId)
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
                        () -> new PhotoNotFoundException(
                                "Photo not found"
                        )
                );
    }

    private Path hotelDirectory(Long hotelId) {
        return storageRoot
                .resolve(String.valueOf(hotelId))
                .normalize();
    }

}
