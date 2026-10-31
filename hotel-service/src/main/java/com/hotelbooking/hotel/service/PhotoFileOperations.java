package com.hotelbooking.hotel.service;

import org.slf4j.Logger;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

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

/** Shared low-level photo validation and transaction-safe filesystem operations. */
final class PhotoFileOperations {
    private PhotoFileOperations() {}

    static void registerDirectoryDeletionAfterCommit(Path directory, Logger log) {
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
                                    "Cannot delete photo directory after commit: {}",
                                    directory,
                                    ex
                            );
                        }
                    }
                }
        );
    }

    // №17: файл, созданный при неудачной транзакции,
    // не должен остаться на диске.
    static void registerRollbackCleanup(Path file, Logger log) {
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
    static void registerDeleteAfterCommit(Path file, Logger log) {
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

    static void validateDecodedImage(MultipartFile file, String expectedType) {
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

    static String detectType(byte[] bytes) {
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
