package com.hotelbooking.hotel.config;

import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.HotelPhotoRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "app.demo.enabled=true", showSql = false)
@Import(DemoHotelsInitializer.class)
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DemoHotelsIdempotencyJpaTest {
    private static final Path BASE = Path.of(System.getProperty("java.io.tmpdir"), "hotel-demo-test-" + UUID.randomUUID());
    @DynamicPropertySource static void paths(DynamicPropertyRegistry registry) {
        registry.add("app.photos.directory", () -> BASE.resolve("hotels").toString());
        registry.add("app.room-photos.directory", () -> BASE.resolve("rooms").toString());
    }
    @Autowired DemoHotelsInitializer initializer;
    @Autowired HotelRepository hotels;
    @Autowired HotelPhotoRepository photos;
    @Autowired RoomRepository rooms;
    @Autowired PlatformTransactionManager transactions;

    @Test void renamedDemoRoomIsNotRecreated() throws Exception {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(s -> { try { initializer.run(new DefaultApplicationArguments()); }
            catch (Exception ex) { throw new IllegalStateException(ex); } });
        assertEquals(9, rooms.count());
        tx.executeWithoutResult(s -> {
            var room = rooms.findAll().stream().filter(r -> "maison-lumiere:101".equals(r.getDemoSeedKey()))
                    .findFirst().orElseThrow();
            room.changeNumber("101-RENAMED");
            rooms.saveAndFlush(room);
        });
        tx.executeWithoutResult(s -> { try { initializer.run(new DefaultApplicationArguments()); }
            catch (Exception ex) { throw new IllegalStateException(ex); } });
        assertEquals(9, rooms.count());
        assertEquals(1, rooms.findAll().stream()
                .filter(room -> "maison-lumiere:101".equals(room.getDemoSeedKey()))
                .count());
    }

    @Test void rollbackRemovesNewlyCopiedDemoFiles() throws Exception {
        var tx = new TransactionTemplate(transactions);
        // ApplicationRunner already seeds demo hotels during Spring context startup.
        long hotelCountBefore = hotels.count();
        var hotel = hotels.findAll().stream()
                .filter(h -> !photos.findByHotel_IdOrderBySortOrderAscIdAsc(h.getId()).isEmpty())
                .findFirst().orElseThrow();
        var photo = photos.findByHotel_IdOrderBySortOrderAscIdAsc(hotel.getId())
                .get(0);
        long photoCountBefore = photos.count();
        Path target = BASE.resolve("hotels").resolve(hotel.getId().toString())
                .resolve(photo.getFileName());
        // Simulate a missing demo image that the next seed invocation must restore.
        // Commit the metadata removal so the initializer actually attempts a copy.
        tx.executeWithoutResult(s -> {
            photos.deleteById(photo.getId());
            photos.flush();
        });
        Files.deleteIfExists(target);
        assertFalse(Files.exists(target));

        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            try { initializer.run(new DefaultApplicationArguments()); }
            catch (Exception ex) { throw new IllegalStateException(ex); }
            assertTrue(Files.exists(target), "Initializer must copy demo photo inside transaction");
            throw new IllegalStateException("force rollback");
        }));
        assertFalse(Files.exists(target), "Rollback must remove newly copied demo file");
        assertEquals(hotelCountBefore, hotels.count());
        assertEquals(photoCountBefore - 1, photos.count(),
                "Rolled-back demo photo metadata must not be persisted");
    }
}
