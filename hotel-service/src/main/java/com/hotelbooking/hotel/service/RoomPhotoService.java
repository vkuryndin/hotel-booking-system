package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.response.RoomPhotoResponse;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomPhoto;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.repository.RoomPhotoRepository;
import com.hotelbooking.hotel.repository.RoomRepository;

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
public class RoomPhotoService {

    private static final Logger log =
            LoggerFactory.getLogger(RoomPhotoService.class);

    private final RoomRepository roomRepository;
    private final RoomPhotoRepository photoRepository;
    private final Path storageRoot;

    public RoomPhotoService(
            RoomRepository roomRepository,
            RoomPhotoRepository photoRepository,
            @Value("${app.room-photos.directory:storage/rooms}")
            String directory
    ) {
        this.roomRepository = roomRepository;
        this.photoRepository = photoRepository;
        this.storageRoot = Path.of(directory)
                .toAbsolutePath()
                .normalize();
    }

    @Transactional(readOnly = true)
    public List<RoomPhotoResponse> list(Long roomId) {
        findRoom(roomId);

        return photoRepository
                .findByRoom_IdOrderBySortOrderAscIdAsc(roomId)
                .stream()
                .map(RoomPhotoResponse::from)
                .toList();
    }

    @Transactional
    public RoomPhotoResponse upload(
            Long roomId,
            MultipartFile file
    ) {
        // №17: сериализуем изменения фотографий одного номера.
        Room room = lockRoom(roomId);

        if (file == null
                || file.isEmpty()
                || file.getSize() > 5L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "Photo must be 1 byte to 5 MB"
            );
        }

        // Проверяем лимит уже под блокировкой номера.
        List<RoomPhoto> existing = photoRepository
                .findByRoom_IdOrderBySortOrderAscIdAsc(roomId);

        if (existing.size() >= 10) {
            throw new IllegalArgumentException(
                    "Maximum 10 photos per room"
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

        // A file with a valid signature may still be truncated or corrupted.
        // Decode the complete image BEFORE writing it to storage or the database.
        validateDecodedImage(file, contentType);

        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".webp";
        };

        String fileName = UUID.randomUUID() + extension;

        Path destination = roomDirectory(roomId)
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
                .mapToInt(RoomPhoto::getSortOrder)
                .max()
                .orElse(-1) + 1;

        RoomPhoto photo = new RoomPhoto(
                room,
                fileName,
                contentType,
                existing.isEmpty(),
                sortOrder
        );

        RoomPhoto saved = photoRepository.saveAndFlush(photo);

        return RoomPhotoResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Resource getFile(
            Long roomId,
            Long photoId
    ) {
        RoomPhoto photo = findPhoto(roomId, photoId);

        Path directory = roomDirectory(roomId);

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
            Long roomId,
            Long photoId
    ) {
        return findPhoto(roomId, photoId).getContentType();
    }

    @Transactional
    public RoomPhotoResponse setPrimary(
            Long roomId,
            Long photoId
    ) {
        // №17: два параллельных PATCH не смогут назначить
        // две главные фотографии одновременно.
        lockRoom(roomId);

        RoomPhoto selected = findPhoto(roomId, photoId);

        List<RoomPhoto> photos = photoRepository
                .findByRoom_IdOrderBySortOrderAscIdAsc(roomId);

        for (RoomPhoto photo : photos) {
            photo.setPrimaryPhoto(
                    photo.getId().equals(selected.getId())
            );
        }

        return RoomPhotoResponse.from(selected);
    }

    @Transactional
    public RoomPhotoResponse setOrder(
            Long roomId,
            Long photoId,
            int sortOrder
    ) {
        if (sortOrder < 0) {
            throw new IllegalArgumentException(
                    "sortOrder must be >= 0"
            );
        }

        // №17: изменение порядка также сериализуем.
        lockRoom(roomId);

        RoomPhoto photo = findPhoto(roomId, photoId);
        photo.setSortOrder(sortOrder);

        return RoomPhotoResponse.from(photo);
    }

    @Transactional
    public void delete(
            Long roomId,
            Long photoId
    ) {
        // №17: защищаем удаление и выбор новой primary.
        lockRoom(roomId);

        RoomPhoto photo = findPhoto(roomId, photoId);

        boolean wasPrimary = photo.isPrimaryPhoto();

        Path directory = roomDirectory(roomId);
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
            List<RoomPhoto> remaining = photoRepository
                    .findByRoom_IdOrderBySortOrderAscIdAsc(roomId);

            if (!remaining.isEmpty()) {
                remaining.get(0).setPrimaryPhoto(true);
            }
        }

        // №17: физический файл удалится только после
        // успешного COMMIT транзакции.
        registerDeleteAfterCommit(file);
    }

    /**
     * Вызывается только из RoomService.deleteRoom() внутри общей транзакции.
     * Удаляем строки из БД; файлы останутся до COMMIT.
     * Блокировка номера уже удерживается вызывающим методом.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeAllForRoomDeletion(Long roomId) {
        List<RoomPhoto> photos = photoRepository
                .findByRoom_IdOrderBySortOrderAscIdAsc(roomId);

        photoRepository.deleteAll(photos);
        photoRepository.flush();

        // Чистим весь каталог данного номера, включая файлы,
        // оставшиеся от предыдущих неуспешных операций.
        registerRoomDirectoryDeletionAfterCommit(roomDirectory(roomId));
    }

    private void registerRoomDirectoryDeletionAfterCommit(Path directory) {
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
                                    "Cannot delete room photo directory after commit: {}",
                                    directory,
                                    ex
                            );
                        }
                    }
                }
        );
    }

    private Room findRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(
                        () -> new RoomNotFoundException(roomId)
                );
    }

    // №17: единая блокировка для всех операций изменения фото.
    private Room lockRoom(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(
                        () -> new RoomNotFoundException(roomId)
                );
    }

    private RoomPhoto findPhoto(
            Long roomId,
            Long photoId
    ) {
        return photoRepository.findById(photoId)
                .filter(
                        photo -> photo.getRoom()
                                .getId()
                                .equals(roomId)
                )
                .orElseThrow(
                        () -> new PhotoNotFoundException(
                                "Photo not found"
                        )
                );
    }

    private Path roomDirectory(Long roomId) {
        return storageRoot
                .resolve(String.valueOf(roomId))
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

    private void validateDecodedImage(MultipartFile file, String expectedType) {
        try (InputStream stream = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(stream)) {
            if (imageInput == null) {
                throw new IllegalArgumentException("Cannot decode photo");
            }
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Unsupported or corrupted image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String decodedFormat = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                String expectedFormat = switch (expectedType) {
                    case "image/jpeg" -> "jpeg";
                    case "image/png" -> "png";
                    case "image/webp" -> "webp";
                    default -> throw new IllegalArgumentException("Unsupported image format");
                };
                if (!decodedFormat.equals(expectedFormat)
                        && !(expectedFormat.equals("jpeg") && decodedFormat.equals("jpg"))) {
                    throw new IllegalArgumentException("Image format does not match its signature");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 20_000_000L) {
                    throw new IllegalArgumentException("Image dimensions are invalid or too large");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                    throw new IllegalArgumentException("Cannot decode complete image");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("Corrupted or unsupported photo", ex);
        }
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