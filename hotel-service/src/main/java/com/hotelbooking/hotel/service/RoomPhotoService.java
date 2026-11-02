package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.response.RoomPhotoResponse;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomPhoto;
import com.hotelbooking.hotel.exception.PhotoNotFoundException;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.repository.RoomPhotoRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RoomPhotoService {

  private static final Logger log = LoggerFactory.getLogger(RoomPhotoService.class);

  private final RoomRepository roomRepository;
  private final RoomPhotoRepository photoRepository;
  private final Path storageRoot;

  public RoomPhotoService(
      RoomRepository roomRepository,
      RoomPhotoRepository photoRepository,
      @Value("${app.room-photos.directory:storage/rooms}") String directory) {
    this.roomRepository = roomRepository;
    this.photoRepository = photoRepository;
    this.storageRoot = Path.of(directory).toAbsolutePath().normalize();
  }

  @Transactional(readOnly = true)
  public List<RoomPhotoResponse> list(Long roomId) {
    findRoom(roomId);

    return photoRepository.findByRoom_IdOrderBySortOrderAscIdAsc(roomId).stream()
        .map(RoomPhotoResponse::from)
        .toList();
  }

  @Transactional
  public RoomPhotoResponse upload(Long roomId, MultipartFile file) {
    // Lock the owner row to serialize changes to its photo list.
    final Room room = lockRoom(roomId);

    PhotoFileOperations.validateSize(file);

    // Check the photo limit while holding the lock.
    List<RoomPhoto> existing = photoRepository.findByRoom_IdOrderBySortOrderAscIdAsc(roomId);

    if (existing.size() >= 10) {
      throw new IllegalArgumentException("Maximum 10 photos per room");
    }

    PhotoFileOperations.StoredPhoto stored =
        PhotoFileOperations.store(file, roomDirectory(roomId), log);

    int sortOrder = existing.stream().mapToInt(RoomPhoto::getSortOrder).max().orElse(-1) + 1;

    RoomPhoto photo =
        new RoomPhoto(room, stored.fileName(), stored.contentType(), existing.isEmpty(), sortOrder);

    RoomPhoto saved = photoRepository.saveAndFlush(photo);

    return RoomPhotoResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public Resource getFile(Long roomId, Long photoId) {
    RoomPhoto photo = findPhoto(roomId, photoId);

    return PhotoFileOperations.readPhoto(roomDirectory(roomId), photo.getFileName());
  }

  @Transactional(readOnly = true)
  public String contentType(Long roomId, Long photoId) {
    return findPhoto(roomId, photoId).getContentType();
  }

  @Transactional
  public RoomPhotoResponse setPrimary(Long roomId, Long photoId) {
    // Selecting the primary photo must be serialized with other photo mutations.
    lockRoom(roomId);

    RoomPhoto selected = findPhoto(roomId, photoId);

    List<RoomPhoto> photos = photoRepository.findByRoom_IdOrderBySortOrderAscIdAsc(roomId);

    for (RoomPhoto photo : photos) {
      photo.setPrimaryPhoto(photo.getId().equals(selected.getId()));
    }

    return RoomPhotoResponse.from(selected);
  }

  @Transactional
  public RoomPhotoResponse setOrder(Long roomId, Long photoId, int sortOrder) {
    if (sortOrder < 0) {
      throw new IllegalArgumentException("sortOrder must be >= 0");
    }

    lockRoom(roomId);

    RoomPhoto photo = findPhoto(roomId, photoId);
    photo.setSortOrder(sortOrder);

    return RoomPhotoResponse.from(photo);
  }

  @Transactional
  public void delete(Long roomId, Long photoId) {
    lockRoom(roomId);

    RoomPhoto photo = findPhoto(roomId, photoId);

    final boolean wasPrimary = photo.isPrimaryPhoto();

    Path file = PhotoFileOperations.resolveStoredPhoto(roomDirectory(roomId), photo.getFileName());

    photoRepository.delete(photo);
    photoRepository.flush();

    if (wasPrimary) {
      List<RoomPhoto> remaining = photoRepository.findByRoom_IdOrderBySortOrderAscIdAsc(roomId);

      if (!remaining.isEmpty()) {
        remaining.get(0).setPrimaryPhoto(true);
      }
    }

    // Filesystem deletion follows the database commit, never precedes it.
    PhotoFileOperations.registerDeleteAfterCommit(file, log);
  }

  /**
   * Called with the room row locked by the outer transaction. Photo metadata is deleted
   * immediately; files are removed only after commit.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void removeAllForRoomDeletion(Long roomId) {
    List<RoomPhoto> photos = photoRepository.findByRoom_IdOrderBySortOrderAscIdAsc(roomId);

    photoRepository.deleteAll(photos);
    photoRepository.flush();

    // Delete the full directory to remove files orphaned by earlier failures.
    PhotoFileOperations.registerDirectoryDeletionAfterCommit(roomDirectory(roomId), log);
  }

  private Room findRoom(Long roomId) {
    return roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException(roomId));
  }

  private Room lockRoom(Long roomId) {
    return roomRepository
        .findByIdForUpdate(roomId)
        .orElseThrow(() -> new RoomNotFoundException(roomId));
  }

  private RoomPhoto findPhoto(Long roomId, Long photoId) {
    return photoRepository
        .findById(photoId)
        .filter(photo -> photo.getRoom().getId().equals(roomId))
        .orElseThrow(() -> new PhotoNotFoundException("Photo not found"));
  }

  private Path roomDirectory(Long roomId) {
    return storageRoot.resolve(String.valueOf(roomId)).normalize();
  }
}
