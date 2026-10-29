package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.repository.HotelPhotoRepository;
import com.hotelbooking.hotel.repository.HotelRepository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import(HotelPhotoService.class)
// Отключаем автоматическую тестовую транзакцию,
// чтобы реально проверять COMMIT и ROLLBACK.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HotelPhotoServiceConcurrencyJpaTest {

    private static final Path STORAGE = Path.of(
            System.getProperty("java.io.tmpdir"),
            "hotel-photos-test-" + UUID.randomUUID()
    );

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add(
                "app.photos.directory",
                () -> STORAGE.toString()
        );
    }

    @Autowired
    HotelRepository hotels;

    @Autowired
    HotelPhotoRepository photos;

    @Autowired
    HotelPhotoService service;

    @Autowired
    PlatformTransactionManager transactionManager;

    private Long hotelId;

    @BeforeEach
    void createHotel() {
        hotelId = hotels.saveAndFlush(
                new Hotel(
                        "Photo Test " + UUID.randomUUID(),
                        "Test Address"
                )
        ).getId();
    }

    // Небольшая корректная PNG-картинка для теста.
    private MockMultipartFile photo() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwC"
                        + "AAAAC0lEQVR42mP8/x8AAwMCAO+/5doAAAAASUVORK5CYII="
        );

        return new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                png
        );
    }

    private Path photoPath(Long photoId) {
        String fileName = photos.findById(photoId)
                .orElseThrow()
                .getFileName();

        return STORAGE
                .resolve(String.valueOf(hotelId))
                .resolve(fileName);
    }

    @Test
    void uploadRollbackRemovesFileAndMetadata() {
        var transaction = new TransactionTemplate(
                transactionManager
        );

        AtomicReference<Path> savedFile = new AtomicReference<>();

        transaction.executeWithoutResult(status -> {
            var uploaded = service.upload(hotelId, photo());

            savedFile.set(photoPath(uploaded.id()));

            assertTrue(Files.exists(savedFile.get()));

            // Принудительно откатываем транзакцию БД.
            status.setRollbackOnly();
        });

        assertEquals(0L, photos.countByHotel_Id(hotelId));
        assertFalse(Files.exists(savedFile.get()));
    }

    @Test
    void deleteRollbackPreservesFileAndMetadata() {
        var uploaded = service.upload(hotelId, photo());
        Path file = photoPath(uploaded.id());

        var transaction = new TransactionTemplate(
                transactionManager
        );

        transaction.executeWithoutResult(status -> {
            service.delete(hotelId, uploaded.id());

            // До COMMIT файл ещё существует.
            assertTrue(Files.exists(file));

            status.setRollbackOnly();
        });

        assertTrue(photos.existsById(uploaded.id()));
        assertTrue(Files.exists(file));
    }

    @Test
    void deleteCommitRemovesFileAndMetadata() {
        var uploaded = service.upload(hotelId, photo());
        Path file = photoPath(uploaded.id());

        assertTrue(Files.exists(file));

        service.delete(hotelId, uploaded.id());

        assertFalse(photos.existsById(uploaded.id()));
        assertFalse(Files.exists(file));
    }

    @Test
    void concurrentUploadsCannotExceedTenPhotos() throws Exception {
        // Заполняем отель девятью фотографиями.
        for (int i = 0; i < 9; i++) {
            service.upload(hotelId, photo());
        }

        assertEquals(9L, photos.countByHotel_Id(hotelId));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> uploadTask = () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Concurrent test start timeout"
                );
            }

            try {
                service.upload(hotelId, photo());
                return true;
            } catch (IllegalArgumentException ex) {
                // Один из запросов должен получить ошибку
                // превышения лимита.
                return false;
            }
        };

        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(uploadTask);
            var second = executor.submit(uploadTask);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            boolean firstSucceeded =
                    first.get(20, TimeUnit.SECONDS);

            boolean secondSucceeded =
                    second.get(20, TimeUnit.SECONDS);

            // Ровно один из двух запросов принят.
            assertTrue(firstSucceeded ^ secondSucceeded);

        } finally {
            executor.shutdownNow();
        }

        assertEquals(10L, photos.countByHotel_Id(hotelId));

        // При десяти фотографиях главная ровно одна.
        long primaryCount = photos
                .findByHotel_IdOrderBySortOrderAscIdAsc(hotelId)
                .stream()
                .filter(p -> p.isPrimaryPhoto())
                .count();

        assertEquals(1L, primaryCount);
    }

    @AfterAll
    static void cleanTestFiles() throws IOException {
        if (!Files.exists(STORAGE)) {
            return;
        }

        try (var paths = Files.walk(STORAGE)) {
            for (Path path : paths
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}