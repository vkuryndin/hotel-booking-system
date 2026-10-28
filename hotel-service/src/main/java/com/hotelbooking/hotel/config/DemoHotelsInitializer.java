
package com.hotelbooking.hotel.config;

import com.hotelbooking.hotel.entity.*;
import com.hotelbooking.hotel.repository.HotelPhotoRepository;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final Path photoStorage;

    public DemoHotelsInitializer(
            HotelRepository hotels,
            RoomRepository rooms,
            HotelPhotoRepository photos,
            @Value("${app.photos.directory:storage/hotels}")
            String photoDirectory
    ) {
        this.hotels = hotels;
        this.rooms = rooms;
        this.photos = photos;
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

                boolean exists = existingRooms.stream()
                        .anyMatch(r -> r.getNumber()
                                .equals(roomSeed.number()));

                if (!exists) {
                    Room room = new Room(hotel, roomSeed.number());

                    room.changeRoomDetails(
                            roomSeed.category(),
                            roomSeed.capacity(),
                            roomSeed.pricePerNight(),
                            roomSeed.amenities()
                    );

                    rooms.save(room);
                }
            }

            List<HotelPhoto> existingPhotos =
                    photos.findByHotel_IdOrderBySortOrderAscIdAsc(hotel.getId());

            Path directory = photoStorage
                    .resolve(hotel.getId().toString())
                    .normalize();

            Files.createDirectories(directory);

            int sortOrder = existingPhotos.size();
            boolean makePrimary = existingPhotos.isEmpty();

            for (PhotoSeed photoSeed : seed.photos()) {

                Path file = directory
                        .resolve(photoSeed.fileName())
                        .normalize();

                if (!file.startsWith(directory)) {
                    throw new IllegalStateException("Invalid demo photo path");
                }

                if (Files.notExists(file)) {
                    ClassPathResource resource = new ClassPathResource(
                            "demo/hotels/" + photoSeed.resourceName()
                    );

                    try (var input = resource.getInputStream()) {
                        Files.copy(input, file);
                    }
                }

                boolean exists = existingPhotos.stream()
                        .anyMatch(p -> p.getFileName()
                                .equals(photoSeed.fileName()));

                if (!exists) {
                    photos.save(new HotelPhoto(
                            hotel,
                            photoSeed.fileName(),
                            "image/jpeg",
                            makePrimary,
                            sortOrder++
                    ));

                    makePrimary = false;
                }
            }
        }

        log.info("Demo initialized: 3 hotels and 9 rooms");
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
                                new PhotoSeed("maison-lumiere.jpg", "demo-maison-lumiere.jpg"),
                                new PhotoSeed("maison-lumiere-2.jpg", "demo-maison-lumiere-2.jpg")
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
                        )
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
                                new PhotoSeed("coral-bay.jpg", "demo-coral-bay.jpg"),
                                new PhotoSeed("coral-bay-2.jpg", "demo-coral-bay-2.jpg")
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
                        )
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
                                new PhotoSeed("alpine-summit.jpg", "demo-alpine-summit.jpg"),
                                new PhotoSeed("alpine-summit-2.jpg", "demo-alpine-summit-2.jpg")
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
                        )
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
            List<RoomSeed> rooms
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
