package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.response.HotelStatisticsResponse;
import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationNightRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HotelStatisticsService {

  private final HotelRepository hotelRepository;
  private final RoomRepository roomRepository;
  private final RoomReservationRepository reservationRepository;
  private final RoomReservationNightRepository nightRepository;

  public HotelStatisticsService(
      HotelRepository hotelRepository,
      RoomRepository roomRepository,
      RoomReservationRepository reservationRepository,
      RoomReservationNightRepository nightRepository) {
    this.hotelRepository = hotelRepository;
    this.roomRepository = roomRepository;
    this.reservationRepository = reservationRepository;
    this.nightRepository = nightRepository;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public HotelStatisticsResponse getStatistics(Long hotelId) {

    if (!hotelRepository.existsById(hotelId)) {
      throw new HotelNotFoundException(hotelId);
    }

    LocalDate today = LocalDate.now();

    long totalRooms = roomRepository.countByHotelId(hotelId);

    long availableRooms = roomRepository.countByHotelIdAndAvailableTrue(hotelId);

    long occupiedToday = nightRepository.countOccupiedRoomsOnDate(hotelId, today);

    long totalReservations = reservationRepository.countByRoom_Hotel_IdAndTombstoneFalse(hotelId);

    long reservedReservations =
        reservationRepository.countByRoom_Hotel_IdAndStatusAndTombstoneFalse(
            hotelId, ReservationStatus.RESERVED);

    long releasedReservations =
        reservationRepository.countByRoom_Hotel_IdAndStatusAndTombstoneFalse(
            hotelId, ReservationStatus.RELEASED);

    // A release-before-reserve marker is not a booking and must not affect statistics.
    long tombstoneReservations =
        reservationRepository.countByRoom_Hotel_IdAndTombstoneTrue(hotelId);

    Long totalTimesBooked = roomRepository.sumTimesBookedByHotelId(hotelId);

    return new HotelStatisticsResponse(
        hotelId,
        today,
        totalRooms,
        availableRooms,
        totalRooms - availableRooms,
        occupiedToday,
        totalReservations,
        reservedReservations,
        releasedReservations,
        totalTimesBooked == null ? 0L : totalTimesBooked,
        tombstoneReservations);
  }
}
