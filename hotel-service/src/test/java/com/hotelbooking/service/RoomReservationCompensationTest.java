
package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(showSql = false)
@Import(RoomReservationService.class)
class RoomReservationCompensationTest {

    @Autowired
    private RoomReservationService reservationService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void failedBookingCompensationRestoresCounter() {

        // 1. Создаём тестовый отель и номер.
        Hotel hotel = hotelRepository.saveAndFlush(
                new Hotel("Compensation Test", "Test Address")
        );

        Room room = roomRepository.saveAndFlush(
                new Room(hotel, "T101")
        );

        Long roomId = room.getId();
        UUID reservationId = UUID.randomUUID();

        assertEquals(0L, counter(roomId));

        // 2. Hotel Service резервирует номер.
        reservationService.reserve(
                reservationId,
                roomId,
                LocalDate.of(2055, 7, 10),
                LocalDate.of(2055, 7, 12),
                1
        );

        assertEquals(1L, counter(roomId));

        // 3. Имитируем ошибку подтверждения
        // бронирования в Booking Service.
        // Вызываем компенсацию.
        reservationService.release(
                reservationId,
                roomId,
                true
        );

        assertEquals(0L, counter(roomId));

        // 4. Повторная компенсация
        // не должна уменьшать счётчик ещё раз.
        reservationService.release(
                reservationId,
                roomId,
                true
        );

        assertEquals(0L, counter(roomId));
    }

    private long counter(Long roomId) {
        entityManager.flush();
        entityManager.clear();

        return roomRepository.findById(roomId)
                .orElseThrow()
                .getTimesBooked();
    }
}
