
package com.hotelbooking.hotel.config;

import com.hotelbooking.hotel.entity.*;
import com.hotelbooking.hotel.repository.HotelPhotoRepository;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomPhotoRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.IOException;
import java.nio.file.StandardCopyOption;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(
        name = "app.demo.enabled",
        havingValue = "true"
)
public class DemoHotelsInitializer implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DemoHotelsInitializer.class);

    private final HotelRepository hotels;
    private final RoomRepository rooms;
    private final HotelPhotoRepository photos;
    private final RoomPhotoRepository roomPhotos;
    private final Path photoStorage;
    private final Path roomPhotoStorage;

    public DemoHotelsInitializer(
            HotelRepository hotels,
            RoomRepository rooms,
            HotelPhotoRepository photos,
            RoomPhotoRepository roomPhotos,
            @Value("${app.photos.directory:storage/hotels}") String photoDirectory,
            @Value("${app.room-photos.directory:storage/rooms}") String roomPhotoDirectory
    ) {
        this.hotels = hotels;
        this.rooms = rooms;
        this.photos = photos;
        this.roomPhotos = roomPhotos;
        this.roomPhotoStorage = Path.of(roomPhotoDirectory).toAbsolutePath().normalize();
        this.photoStorage = Path.of(photoDirectory)
                .toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {

        for (HotelSeed seed : demoHotels()) {

            Hotel hotel = hotels.findAll().stream()
                    .filter(h ->
                            h.getName().equalsIgnoreCase(seed.name())
                                    && h.getAddress().equalsIgnoreCase(seed.address()))
                    .findFirst()
                    .orElseGet(() -> hotels.saveAndFlush(
                            new Hotel(seed.name(), seed.address())
                    ));

            if (hotel.getDescription() == null) {
                hotel.changePresentation(
                        seed.description(),
                        seed.rating(),
                        seed.amenities(),
                        LocalTime.of(14, 0),
                        LocalTime.of(12, 0)
                );
            }

            List<Room> existingRooms =
                    rooms.findByHotelId(hotel.getId());

            for (RoomSeed roomSeed : seed.rooms()) {
                String key = seed.photoPrefix() + ":" + roomSeed.number();
                Room room = existingRooms.stream()
                        .filter(r -> key.equals(r.getDemoSeedKey()))
                        .findFirst().orElse(null);
                if (room == null) {
                    // One-time migration of previously seeded rooms that lack the identity marker.
                    room = existingRooms.stream()
                            .filter(r -> r.getDemoSeedKey() == null
                                    && r.getNumber().equals(roomSeed.number()))
                            .findFirst().orElse(null);
                }
                if (room == null) {
                    room = new Room(hotel, roomSeed.number());
                    room.changeRoomDetails(roomSeed.category(), roomSeed.capacity(),
                            roomSeed.pricePerNight(), roomSeed.amenities());
                }
                room.assignDemoSeedKey(key);
                room = rooms.saveAndFlush(room);
                // Use original seed number; an admin may have renamed the room.
                seedRoomPhotos(room, seed.photoPrefix(), roomSeed.number());
            }

            seedHotelPhotos(hotel, seed.photos());
        }

        log.info("Demo initialized: 3 hotels and 9 rooms; available photos synchronized");
    }

    /** Existing demo files are imported once. Missing optional files do not block startup. */
    private void seedHotelPhotos(Hotel hotel, List<PhotoSeed> seeds) throws Exception {
        List<HotelPhoto> existing =
                photos.findByHotel_IdOrderBySortOrderAscIdAsc(hotel.getId());
        Path directory = photoStorage.resolve(hotel.getId().toString()).normalize();
        if (!directory.startsWith(photoStorage)) {
            throw new IllegalStateException("Invalid hotel photo directory");
        }
        int order = existing.stream().mapToInt(HotelPhoto::getSortOrder)
                .max().orElse(-1) + 1;
        boolean primary = existing.isEmpty();
        for (PhotoSeed photo : seeds) {
            if (existing.stream().anyMatch(p -> p.getFileName().equals(photo.fileName()))) {
                continue;
            }
            ClassPathResource resource = new ClassPathResource("demo/hotels/" + photo.resourceName());
            if (!resource.exists()) {
                log.warn("Optional demo hotel photo not found (demo/hotels/): {}", photo.resourceName());
                continue;
            }
            Path target = directory.resolve(photo.fileName()).normalize();
            if (!target.startsWith(directory)) {
                throw new IllegalStateException("Invalid hotel photo path");
            }
            copyDemoPhoto(resource, directory, target);
            photos.save(new HotelPhoto(hotel, photo.fileName(),
                    photoContentType(photo.fileName()), primary, order++));
            primary = false;
        }
    }

    /** Up to five demo room photos from HOTEL/ROOM/number/photo_1..5; stored with legacy unique names. */
    private void seedRoomPhotos(Room room, String prefix, String seedNumber) throws Exception {
        List<RoomPhoto> existing = roomPhotos
                .findByRoom_IdOrderBySortOrderAscIdAsc(room.getId());
        Path directory = roomPhotoStorage.resolve(room.getId().toString()).normalize();
        if (!directory.startsWith(roomPhotoStorage)) {
            throw new IllegalStateException("Invalid room photo directory");
        }
        int order = existing.stream().mapToInt(RoomPhoto::getSortOrder)
                .max().orElse(-1) + 1;
        boolean primary = existing.isEmpty();
        for (int i = 1; i <= 5; i++) {
            String stem = prefix + "-room-" + seedNumber + (i == 1 ? "" : "-" + i);
            // Extension is determined by the actual classpath resource.
            String fileName = null;
            ClassPathResource resource = null;
            for (String extension : List.of(".jpg", ".jpeg", ".png")) {
                ClassPathResource candidate = new ClassPathResource("demo/hotels/" + prefix + "/ROOM/" + seedNumber + "/photo_" + i + extension);
                if (candidate.exists()) {
                    fileName = stem + extension;
                    resource = candidate;
                    break;
                }
            }
            if (resource == null) {
                log.debug("Optional demo room photo missing: {}", stem);
                continue;
            }
            final String current = fileName;
            if (existing.stream().anyMatch(p -> p.getFileName().equals(current))) {
                continue;
            }
            Path target = directory.resolve(fileName).normalize();
            if (!target.startsWith(directory)) {
                throw new IllegalStateException("Invalid room photo path");
            }
            copyDemoPhoto(resource, directory, target);
            roomPhotos.save(new RoomPhoto(room, fileName,
                    photoContentType(fileName), primary, order++));
            primary = false;
        }
    }

    /** Writes demo images atomically; if the surrounding DB transaction rolls back,
     * remove newly copied files to avoid unreferenced on-disk data. */
    private void copyDemoPhoto(ClassPathResource resource, Path directory, Path target) throws IOException {
        Files.createDirectories(directory);
        if (Files.exists(target)) return;
        Path staged = Files.createTempFile(directory, ".demo-", ".tmp");
        boolean installed = false;
        try {
            try (var input = resource.getInputStream()) {
                Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(staged, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(staged, target);
            }
            installed = true;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            try { Files.deleteIfExists(target); }
                            catch (IOException ex) { log.error("Cannot compensate demo photo {}", target, ex); }
                        }
                    }
                });
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    static String photoContentType(String fileName) {
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        throw new IllegalArgumentException("Unsupported demo photo extension: " + fileName);
    }

    private static List<HotelSeed> demoHotels() {
        return List.of(
                new HotelSeed(
                        "Maison Lumiere Paris",
                        "12 Rue des Jardins, Paris, France",
                        "Elegant boutique hotel in central Paris, " +
                                "with spacious rooms and personalized service.",
                        new BigDecimal("4.8"),
                        Set.of(
                                HotelAmenity.WIFI,
                                HotelAmenity.RESTAURANT,
                                HotelAmenity.GYM
                        ),
                        List.of(
                                new PhotoSeed("maison-lumiere/maison-lumiere.jpg", "maison-lumiere.jpg"),
                                new PhotoSeed("maison-lumiere/maison-lumiere-2.png", "maison-lumiere-2.png"),
                                new PhotoSeed("maison-lumiere/maison-lumiere-3.jpg", "maison-lumiere-3.jpg"),
                                new PhotoSeed("maison-lumiere/maison-lumiere-4.jpg", "maison-lumiere-4.jpg"),
                                new PhotoSeed("maison-lumiere/maison-lumiere-5.jpg", "maison-lumiere-5.jpg")
                        ),
                        List.of(
                                new RoomSeed("101", RoomCategory.STANDARD,
                                        2, "160.00",
                                        Set.of(RoomAmenity.WIFI,
                                                RoomAmenity.TV,
                                                RoomAmenity.AIR_CONDITIONING)),
                                new RoomSeed("201", RoomCategory.DELUXE,
                                        3, "240.00",
                                        Set.of(RoomAmenity.WIFI,
                                                RoomAmenity.TV,
                                                RoomAmenity.MINIBAR)),
                                new RoomSeed("301", RoomCategory.SUITE,
                                        4, "390.00",
                                        Set.of(RoomAmenity.WIFI,
                                                RoomAmenity.TV,
                                                RoomAmenity.MINIBAR,
                                                RoomAmenity.BALCONY))
                        ),
                        "maison-lumiere"
                ),
                new HotelSeed(
                        "Coral Bay Resort",
                        "8 Seafront Avenue, Antalya, Turkey",
                        "Seaside resort with a pool, beachfront dining " +
                                "and family-friendly accommodation.",
                        new BigDecimal("4.9"),
                        Set.of(
                                HotelAmenity.WIFI,
                                HotelAmenity.POOL,
                                HotelAmenity.GYM,
                                HotelAmenity.RESTAURANT,
                                HotelAmenity.PARKING
                        ),
                        List.of(
                                new PhotoSeed("coral-bay/coral-bay.jpg", "coral-bay.jpg"),
                                new PhotoSeed("coral-bay/coral-bay-2.png", "coral-bay-2.png"),
                                new PhotoSeed("coral-bay/coral-bay-3.jpg", "coral-bay-3.jpg"),
                                new PhotoSeed("coral-bay/coral-bay-4.jpg", "coral-bay-4.jpg"),
                                new PhotoSeed("coral-bay/coral-bay-5.jpg", "coral-bay-5.jpg")
                        ),
                        List.of(
                                new RoomSeed("101", RoomCategory.STANDARD,
                                        2, "180.00",
                                        Set.of(RoomAmenity.WIFI,
                                                RoomAmenity.TV,
                                                RoomAmenity.AIR_CONDITIONING)),
                                new RoomSeed("201", RoomCategory.DELUXE,
                                        4, "320.00",
                                        Set.of(RoomAmenity.WIFI,
                                                RoomAmenity.TV,
                                                RoomAmenity.AIR_CONDITIONING,
                                                RoomAmenity.BALCONY)),
                                new RoomSeed("301", RoomCategory.SUITE,
                                        5, "540.00",
                                        Set.of(RoomAmenity.WIFI,
                                                RoomAmenity.TV,
                                                RoomAmenity.MINIBAR,
                                                RoomAmenity.BALCONY))
                        ),
                        "coral-bay"
                ),
                new HotelSeed(
                        "Alpine Summit Lodge",
                        "5 Mountain Road, Zermatt, Switzerland",
                        "Alpine mountain retreat with panoramic views, " +
                                "comfortable suites and mountain dining.",
                        new BigDecimal("4.7"),
                        Set.of(
                                HotelAmenity.WIFI,
                                HotelAmenity.RESTAURANT,
                                HotelAmenity.GYM,
                                HotelAmenity.PARKING
                        ),
                        List.of(
                                new PhotoSeed("alpine-summit/alpine-summit.jpg", "alpine-summit.jpg"),
                                new PhotoSeed("alpine-summit/alpine-summit-2.png", "alpine-summit-2.png"),
                                new PhotoSeed("alpine-summit/alpine-summit-3.jpg", "alpine-summit-3.jpg"),
                                new PhotoSeed("alpine-summit/alpine-summit-4.jpg", "alpine-summit-4.jpg"),
                                new PhotoSeed("alpine-summit/alpine-summit-5.jpg", "alpine-summit-5.jpg")
                        ),
                        List.of(
                                new RoomSeed("101", RoomCategory.STANDARD,
                                        2, "210.00",
                                        Set.of(RoomAmenity.WIFI,
                                                RoomAmenity.TV)),
                                new RoomSeed("201", RoomCategory.DELUXE,
                                        3, "340.00",
                                        Set.of(RoomAmenity.WIFI,
                                                RoomAmenity.TV,
                                                RoomAmenity.BALCONY)),
                                new RoomSeed("301", RoomCategory.SUITE,
                                        6, "620.00",
                                        Set.of(RoomAmenity.WIFI,
                                                RoomAmenity.TV,
                                                RoomAmenity.MINIBAR,
                                                RoomAmenity.BALCONY))
                        ),
                        "alpine-summit"
                )
        );
    }

    private record HotelSeed(
            String name,
            String address,
            String description,
            BigDecimal rating,
            Set<HotelAmenity> amenities,
            List<PhotoSeed> photos,
            List<RoomSeed> rooms,
            String photoPrefix
    ) {}

    private record PhotoSeed(
            String resourceName,
            String fileName
    ) {}
    private record RoomSeed(
            String number,
            RoomCategory category,
            Integer capacity,
            BigDecimal pricePerNight,
            Set<RoomAmenity> amenities
    ) {
        private RoomSeed(
                String number,
                RoomCategory category,
                Integer capacity,
                String price,
                Set<RoomAmenity> amenities
        ) {
            this(number, category, capacity,
                    new BigDecimal(price), amenities);
        }
    }
}
