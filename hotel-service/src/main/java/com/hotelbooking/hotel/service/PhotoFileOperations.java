package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.exception.PhotoNotFoundException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** Image validation and filesystem operations coordinated with database transactions. */
final class PhotoFileOperations {
  private PhotoFileOperations() {}

  record StoredPhoto(String fileName, String contentType) {}

  static void validateSize(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
      throw new IllegalArgumentException("Photo must be 1 byte to 5 MB");
    }
  }

  /** Validates and copies a photo, with rollback cleanup registered before writing. */
  static StoredPhoto store(MultipartFile file, Path directory, Logger log) {
    validateSize(file);

    String contentType;
    try (InputStream input = file.getInputStream()) {
      contentType = detectType(input.readNBytes(12));
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot read photo", ex);
    }
    if (contentType == null) {
      throw new IllegalArgumentException("Only valid JPEG, PNG, WebP photos are allowed");
    }

    // Signatures alone cannot rule out truncated or oversized decoded images.
    validateDecodedImage(file, contentType);
    String extension =
        switch (contentType) {
          case "image/jpeg" -> ".jpg";
          case "image/png" -> ".png";
          default -> ".webp";
        };
    String fileName = UUID.randomUUID() + extension;
    Path destination = directory.resolve(fileName);
    registerRollbackCleanup(destination, log);
    try {
      Files.createDirectories(directory);
      try (InputStream input = file.getInputStream()) {
        Files.copy(input, destination);
      }
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot store photo", ex);
    }
    return new StoredPhoto(fileName, contentType);
  }

  static Path resolveStoredPhoto(Path directory, String fileName) {
    Path file = directory.resolve(fileName).normalize();
    if (!file.startsWith(directory)) {
      throw new IllegalArgumentException("Invalid photo path");
    }
    return file;
  }

  // Read eagerly: streaming could race with a post-commit file deletion.
  static Resource readPhoto(Path directory, String fileName) {
    Path file = resolveStoredPhoto(directory, fileName);
    try {
      return new ByteArrayResource(Files.readAllBytes(file));
    } catch (java.nio.file.NoSuchFileException ex) {
      throw new PhotoNotFoundException("Photo file not found");
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot read photo file", ex);
    }
  }

  static void registerDirectoryDeletionAfterCommit(Path directory, Logger log) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                return;
              }

              // Do not follow symlinks when recursively deleting stored image directories.
              Files.walkFileTree(
                  directory,
                  new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                      Files.delete(file);
                      return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException error)
                        throws IOException {
                      if (error != null) {
                        throw error;
                      }
                      Files.delete(dir);
                      return FileVisitResult.CONTINUE;
                    }
                  });
            } catch (IOException ex) {
              // A post-commit failure cannot roll back the database; log the orphaned files.
              log.error("Cannot delete photo directory after commit: {}", directory, ex);
            }
          }
        });
  }

  // Remove files created by transactions that never commit.
  static void registerRollbackCleanup(Path file, Logger log) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {

          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              try {
                Files.deleteIfExists(file);
              } catch (IOException ex) {
                log.warn("Cannot clean up photo after rollback: {}", file, ex);
              }
            }
          }
        });
  }

  // Avoid deleting files for a database change that may still roll back.
  static void registerDeleteAfterCommit(Path file, Logger log) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {

          @Override
          public void afterCommit() {
            try {
              Files.deleteIfExists(file);
            } catch (IOException ex) {
              // Surface post-commit deletion failures in the log for later cleanup.
              log.error("Cannot delete photo file after commit: {}", file, ex);
            }
          }
        });
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
        String expectedFormat =
            switch (expectedType) {
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
            Arrays.copyOf(bytes, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
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
