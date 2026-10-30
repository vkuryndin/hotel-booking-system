package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.PhotoNotFoundException;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import(RoomPhotoService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoomPhotoServiceJpaTest {
    private static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"),
            "room-photo-test-" + UUID.randomUUID());

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("app.room-photos.directory", STORAGE::toString);
    }

    @Autowired HotelRepository hotels;
    @Autowired RoomRepository rooms;
    @Autowired RoomPhotoService photos;
    @Autowired com.hotelbooking.hotel.repository.RoomReservationRepository reservations;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    private Long roomId() {
        Hotel hotel = hotels.saveAndFlush(new Hotel("Photo test " + UUID.randomUUID(), "Address"));
        return rooms.saveAndFlush(new Room(hotel, "101")).getId();
    }

    private MockMultipartFile image() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", out));
        return new MockMultipartFile("file", "room.png", "image/png", out.toByteArray());
    }

    @Test
    void uploadListReadSelectPrimaryAndDelete() throws Exception {
        Long roomId = roomId();
        var first = photos.upload(roomId, image());
        var second = photos.upload(roomId, image());
        assertTrue(first.primary());
        assertFalse(second.primary());
        assertEquals(2, photos.list(roomId).size());
        Resource resource = photos.getFile(roomId, first.id());
        assertTrue(resource.contentLength() > 0);
        assertEquals("image/png", photos.contentType(roomId, first.id()));
        assertTrue(photos.setPrimary(roomId, second.id()).primary());
        assertFalse(photos.list(roomId).get(0).primary());
        assertEquals(4, photos.setOrder(roomId, second.id(), 4).sortOrder());
        photos.delete(roomId, second.id());
        assertTrue(photos.list(roomId).get(0).primary());
        assertThrows(PhotoNotFoundException.class, () -> photos.getFile(roomId, second.id()));
    }

    @Test
    void invalidImageRejected() {
        Long roomId = roomId();
        assertThrows(IllegalArgumentException.class, () -> photos.upload(roomId,
                new MockMultipartFile("file", "bad.png", "image/png", new byte[] {1, 2, 3})));
    }
    @Test
    void deletingRoomWithPhotosRemovesDatabaseRowsAndDiskFiles() throws Exception {
        Long id = roomId();
        var uploaded = photos.upload(id, image());
        Path folder = STORAGE.resolve(id.toString());
        Path file;
        try (var files = java.nio.file.Files.list(folder)) {
            file = files.findFirst().orElseThrow();
        }
        assertTrue(java.nio.file.Files.exists(file));
        var service = new RoomService(rooms, hotels,
                org.mockito.Mockito.mock(com.hotelbooking.hotel.mapper.RoomMapper.class),
                reservations,
                org.mockito.Mockito.mock(com.hotelbooking.hotel.strategy.RoomSelectionStrategy.class),
                org.mockito.Mockito.mock(com.hotelbooking.hotel.strategy.MostExpensiveStrategy.class), photos);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> service.deleteRoom(id));
        assertFalse(rooms.existsById(id));
        assertFalse(java.nio.file.Files.exists(file));
        assertThrows(com.hotelbooking.hotel.exception.RoomNotFoundException.class, () -> photos.list(id));
    }

}
