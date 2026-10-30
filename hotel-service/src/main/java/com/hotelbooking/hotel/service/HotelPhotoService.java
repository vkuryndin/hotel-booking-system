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
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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

        // №17: если БД откатится, удаляем созданный файл.
        // Регистрируем ДО копирования, чтобы убрать даже частично
        // записанный файл при ошибке Files.copy().
        registerRollbackCleanup(destination);

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
        registerDeleteAfterCommit(file);
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
        registerHotelDirectoryDeletionAfterCommit(hotelDirectory(hotelId));
    }

    private void registerHotelDirectoryDeletionAfterCommit(Path directory) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                                return;
                            }

                            // Без FOLLOW_LINKS: не выходим за пределы
                            // каталога через символические ссылки.
                            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                                @Override
                                public FileVisitResult visitFile(
                                        Path file,
                                        BasicFileAttributes attrs
                                ) throws IOException {
                                    Files.delete(file);
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public FileVisitResult postVisitDirectory(
                                        Path dir,
                                        IOException error
                                ) throws IOException {
                                    if (error != null) {
                                        throw error;
                                    }
                                    Files.delete(dir);
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        } catch (IOException ex) {
                            // COMMIT БД уже произошёл; фиксируем сбой,
                            // чтобы можно было очистить остатки позже.
                            log.error(
                                    "Cannot delete hotel photo directory after commit: {}",
                                    directory,
                                    ex
                            );
                        }
                    }
                }
        );
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

    // №17: файл, созданный при неудачной транзакции,
    // не должен остаться на диске.
    private void registerRollbackCleanup(Path file) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            try {
                                Files.deleteIfExists(file);
                            } catch (IOException ex) {
                                log.warn(
                                        "Cannot clean up photo after rollback: {}",
                                        file,
                                        ex
                                );
                            }
                        }
                    }
                }
        );
    }

    // №17: не удаляем файл до фиксации удаления в БД.
    private void registerDeleteAfterCommit(Path file) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException ex) {
                            // COMMIT уже произошёл: откатить БД нельзя.
                            // Ошибку обязательно фиксируем в журнале.
                            log.error(
                                    "Cannot delete photo file after commit: {}",
                                    file,
                                    ex
                            );
                        }
                    }
                }
        );
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