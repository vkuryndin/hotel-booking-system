package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.repository.HotelRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HotelCaseInsensitiveUniquenessJpaTest {

    @Autowired
    HotelRepository hotels;

    @Test
    void differentCaseCannotCreateDuplicateHotel() {
        String suffix = UUID.randomUUID().toString();

        hotels.saveAndFlush(
                new Hotel("Ararat-" + suffix, "Yerevan")
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> hotels.saveAndFlush(
                        new Hotel("ARARAT-" + suffix, "YEREVAN")
                )
        );
    }

    @Test
    void differentAddressAllowsSameHotelName() {
        String suffix = UUID.randomUUID().toString();

        Hotel first = hotels.saveAndFlush(
                new Hotel("Ararat-" + suffix, "Yerevan")
        );

        Hotel second = hotels.saveAndFlush(
                new Hotel("ARARAT-" + suffix, "Gyumri")
        );

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void updateCannotDuplicateHotelWithDifferentCase() {
        String suffix = UUID.randomUUID().toString();

        hotels.saveAndFlush(
                new Hotel("Ararat-" + suffix, "Yerevan")
        );

        Hotel other = hotels.saveAndFlush(
                new Hotel("Other-" + suffix, "Gyumri")
        );

        other.updateDetails("ARARAT-" + suffix, "YEREVAN");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> hotels.saveAndFlush(other)
        );
    }
}