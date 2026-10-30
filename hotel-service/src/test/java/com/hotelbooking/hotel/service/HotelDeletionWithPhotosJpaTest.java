package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.ResourceInUseException;
import com.hotelbooking.hotel.mapper.HotelMapper;
import com.hotelbooking.hotel.repository.HotelPhotoRepository;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import({HotelService.class, HotelPhotoService.class})
// Для проверки COMMIT/ROLLBACK нужна настоящая транзакция сервиса.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HotelDeletionWithPhotosJpaTest {

    private static final Path STORAGE = Path.of(
            System.getProperty("java.io.tmpdir"),
            "hotel-delete-test-" + UUID.randomUUID()
    );

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.photos.directory", () -> STORAGE.toString());
    }

    @Autowired HotelRepository hotels;
    @Autowired RoomRepository rooms;
    @Autowired HotelPhotoRepository photos;
    @Autowired HotelPhotoService photoService;
    @Autowired HotelService hotelService;
    @Autowired PlatformTransactionManager txManager;

    // HotelMapper требуется для конструктора HotelService,
    // однако удаление не использует этот mapper.
    @MockitoBean HotelMapper hotelMapper;

    private Long hotelId;

    @BeforeEach
    void setup() {
        hotelId = hotels.saveAndFlush(
                new Hotel("Delete Test " + UUID.randomUUID(), "Test Street")
        ).getId();
    }

    private MockMultipartFile image() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwC"
                        + "AAAAC0lEQVR42mP8/x8AAwMCAO+/5doAAAAASUVORK5CYII="
        );
        return new MockMultipartFile("file", "photo.png", "image/png", png);
    }

    private Path hotelDirectory() {
        return STORAGE.resolve(String.valueOf(hotelId));
    }

    @Test
    void commitRemovesHotelPhotoRowsFilesAndDirectory() {
        photoService.upload(hotelId, image());
        photoService.upload(hotelId, image());

        assertEquals(2, photos.countByHotel_Id(hotelId));
        assertTrue(Files.exists(hotelDirectory()));

        hotelService.deleteHotel(hotelId);

        assertFalse(hotels.existsById(hotelId));
        assertEquals(0, photos.countByHotel_Id(hotelId));
        assertFalse(Files.exists(hotelDirectory()));
    }

    @Test
    void rollbackKeepsHotelPhotoMetadataAndFiles() {
        photoService.upload(hotelId, image());
        Path directory = hotelDirectory();
        assertTrue(Files.exists(directory));

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            hotelService.deleteHotel(hotelId);

            // Файл нельзя удалять до фиксации БД.
            assertTrue(Files.exists(directory));
            status.setRollbackOnly();
        });

        assertTrue(hotels.existsById(hotelId));
        assertEquals(1, photos.countByHotel_Id(hotelId));
        assertTrue(Files.exists(directory));
    }

    @Test
    void hotelWithRoomsIsNotDeletedAndPhotosStay() {
        photoService.upload(hotelId, image());
        rooms.saveAndFlush(new Room(hotels.findById(hotelId).orElseThrow(), "101"));

        ResourceInUseException error = assertThrows(
                ResourceInUseException.class,
                () -> hotelService.deleteHotel(hotelId)
        );
        assertEquals("HOTEL_HAS_ROOMS", error.getCode());

        assertTrue(hotels.existsById(hotelId));
        assertEquals(1, photos.countByHotel_Id(hotelId));
        assertTrue(Files.exists(hotelDirectory()));
    }

    @Test
    void hotelWithoutPhotosCanBeDeleted() {
        hotelService.deleteHotel(hotelId);
        assertFalse(hotels.existsById(hotelId));
    }

    @AfterAll
    static void deleteTestStorage() throws IOException {
        if (!Files.exists(STORAGE)) {
            return;
        }
        try (var paths = Files.walk(STORAGE)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
