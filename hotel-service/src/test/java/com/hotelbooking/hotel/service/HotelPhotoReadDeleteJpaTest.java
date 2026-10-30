package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.exception.PhotoNotFoundException;
import com.hotelbooking.hotel.repository.HotelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import(HotelPhotoService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HotelPhotoReadDeleteJpaTest {
    private static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"),
            "hotel-photo-read-delete-" + UUID.randomUUID());

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.photos.directory", STORAGE::toString);
    }

    @Autowired HotelRepository hotels;
    @Autowired HotelPhotoService service;

    @Test
    void obtainedResourceRemainsReadableAfterPhotoDeletion() throws Exception {
        long hotelId = hotels.saveAndFlush(new Hotel("Photo read test " + UUID.randomUUID(), "Address")).getId();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", out));
        byte[] data = out.toByteArray();
        var created = service.upload(hotelId,
                new MockMultipartFile("file", "test.png", "image/png", data));

        Resource resource = service.getFile(hotelId, created.id());
        service.delete(hotelId, created.id());

        assertArrayEquals(data, resource.getInputStream().readAllBytes());
    }

    @Test
    void missingPhysicalFileReturnsPhotoNotFound() throws Exception {
        long hotelId = hotels.saveAndFlush(new Hotel("Missing photo " + UUID.randomUUID(), "Address")).getId();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", out));
        var created = service.upload(hotelId,
                new MockMultipartFile("file", "test.png", "image/png", out.toByteArray()));
        try (var files = Files.list(STORAGE.resolve(Long.toString(hotelId)))) {
            Files.delete(files.findFirst().orElseThrow());
        }
        assertThrows(PhotoNotFoundException.class, () -> service.getFile(hotelId, created.id()));
    }
}
