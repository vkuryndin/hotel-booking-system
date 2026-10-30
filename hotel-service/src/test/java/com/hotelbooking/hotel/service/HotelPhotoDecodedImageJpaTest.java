package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.repository.HotelPhotoRepository;
import com.hotelbooking.hotel.repository.HotelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
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
class HotelPhotoDecodedImageJpaTest {
    static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"),
            "hotel-image-decode-test-" + UUID.randomUUID());
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("app.photos.directory", STORAGE::toString);
    }
    @Autowired HotelRepository hotels;
    @Autowired HotelPhotoRepository photos;
    @Autowired HotelPhotoService photoService;
    Long hotelId;

    @BeforeEach
    void setup() {
        hotelId = hotels.saveAndFlush(new Hotel("Decoded " + UUID.randomUUID(), "Test address")).getId();
    }

    private byte[] sample(String format) throws Exception {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image, format, output));
            return output.toByteArray();
        }
    }

    private void invalid(byte[] bytes, String name) {
        assertThrows(IllegalArgumentException.class, () -> photoService.upload(hotelId,
                new MockMultipartFile("file", name, "image/png", bytes)));
        assertEquals(0, photos.countByHotel_Id(hotelId));
        Path dir = STORAGE.resolve(String.valueOf(hotelId));
        if (Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) { assertEquals(0, files.count()); }
            catch (Exception ex) { fail(ex); }
        }
    }

    @Test
    void validJpegAndPngAreAccepted() throws Exception {
        photoService.upload(hotelId, new MockMultipartFile("file", "ok.png", "image/png", sample("png")));
        photoService.upload(hotelId, new MockMultipartFile("file", "ok.jpg", "image/jpeg", sample("jpeg")));
        assertEquals(2, photos.countByHotel_Id(hotelId));
    }

    @Test
    void truncatedPngWithCorrectHeaderIsRejectedWithoutStoredFiles() throws Exception {
        byte[] png = sample("png");
        invalid(java.util.Arrays.copyOf(png, 16), "broken.png");
    }

    @Test
    void truncatedJpegWithCorrectHeaderIsRejectedWithoutStoredFiles() throws Exception {
        byte[] jpg = sample("jpeg");
        invalid(java.util.Arrays.copyOf(jpg, 12), "broken.jpg");
    }

    @Test
    void truncatedWebpHeaderIsRejected() {
        byte[] header = new byte[] {'R','I','F','F', 28,0,0,0, 'W','E','B','P'};
        invalid(header, "broken.webp");
    }

    @Test
    void validWebpIsAccepted() throws Exception {
        try (var stream = getClass().getResourceAsStream("/stage30-valid.webp")) {
            assertNotNull(stream);
            byte[] image = stream.readAllBytes();
            photoService.upload(hotelId, new MockMultipartFile("file", "ok.webp", "image/webp", image));
            assertEquals(1, photos.countByHotel_Id(hotelId));
        }
    }
}
