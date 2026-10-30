package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.HotelAmenity;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomAmenity;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks actual SQL statements: amenities must be loaded in batches,
 * without fetching a collection together with a pageable root query.
 */
@DataJpaTest(showSql = false)
class HotelAmenitiesBatchJpaTest {

    @Autowired
    private HotelRepository hotels;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void pagedHotelsLoadAmenitiesWithoutNPlusOne() {
        String prefix = "s19-h-" + UUID.randomUUID() + "-";

        for (int i = 0; i < 25; i++) {
            Hotel hotel = new Hotel(prefix + i, "Address " + i);
            hotel.changePresentation(
                    null, null,
                    Set.of(HotelAmenity.WIFI, HotelAmenity.PARKING),
                    null, null
            );
            hotels.save(hotel);
        }

        entityManager.flush();
        entityManager.clear();

        Statistics stats = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        boolean previouslyEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear();

            Specification<Hotel> filter = (root, query, cb) ->
                    cb.like(root.get("name"), prefix + "%");

            Page<Hotel> firstPage = hotels.findAll(
                    filter,
                    PageRequest.of(0, 20, Sort.by("id"))
            );

            assertEquals(25, firstPage.getTotalElements());
            assertEquals(20, firstPage.getContent().size());

            // Same access pattern as HotelMapper when constructing DTOs.
            for (Hotel hotel : firstPage.getContent()) {
                assertEquals(
                        Set.of(HotelAmenity.WIFI, HotelAmenity.PARKING),
                        hotel.getAmenities()
                );
            }

            long sqlQueries = stats.getPrepareStatementCount();
            assertTrue(sqlQueries <= 5,
                    () -> "Hotel amenities N+1 suspected, SQL queries="
                            + sqlQueries);
        } finally {
            stats.setStatisticsEnabled(previouslyEnabled);
        }
    }

    @Test
    void pagedRoomsLoadAmenitiesWithoutNPlusOne() {
        String prefix = "s19-r-" + UUID.randomUUID();
        Hotel hotel = hotels.saveAndFlush(
                new Hotel(prefix, "Address")
        );

        for (int i = 0; i < 25; i++) {
            Room room = new Room(hotel, "R-" + i);
            room.changeRoomDetails(
                    null, null, null,
                    Set.of(RoomAmenity.WIFI, RoomAmenity.TV)
            );
            rooms.save(room);
        }

        entityManager.flush();
        entityManager.clear();

        Statistics stats = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        boolean previouslyEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear();

            Specification<Room> filter = (root, query, cb) ->
                    cb.equal(root.get("hotel").get("id"), hotel.getId());

            Page<Room> firstPage = rooms.findAll(
                    filter,
                    PageRequest.of(0, 20, Sort.by("id"))
            );

            assertEquals(25, firstPage.getTotalElements());
            assertEquals(20, firstPage.getContent().size());

            // Same access pattern as RoomMapper when constructing DTOs.
            for (Room room : firstPage.getContent()) {
                assertEquals(
                        Set.of(RoomAmenity.WIFI, RoomAmenity.TV),
                        room.getAmenities()
                );
            }

            long sqlQueries = stats.getPrepareStatementCount();
            assertTrue(sqlQueries <= 5,
                    () -> "Room amenities N+1 suspected, SQL queries="
                            + sqlQueries);
        } finally {
            stats.setStatisticsEnabled(previouslyEnabled);
        }
    }
}
