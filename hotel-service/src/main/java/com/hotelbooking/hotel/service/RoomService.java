package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.request.RoomAvailabilityRequest;
import com.hotelbooking.hotel.dto.request.RoomRequest;
import com.hotelbooking.hotel.dto.request.RoomUpdateRequest;
import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.exception.ResourceInUseException;
import com.hotelbooking.hotel.exception.RoomAlreadyExistsException;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.strategy.MostExpensiveStrategy;
import com.hotelbooking.hotel.strategy.RoomSelectionMode;
import com.hotelbooking.hotel.strategy.RoomSelectionStrategy;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

  private final RoomRepository roomRepository;
  private final HotelRepository hotelRepository;
  private final RoomMapper roomMapper;
  private final RoomReservationRepository reservationRepository;
  private final RoomSelectionStrategy selectionStrategy;
  private final MostExpensiveStrategy mostExpensiveStrategy;
  private final RoomPhotoService roomPhotoService;

  public RoomService(
      RoomRepository roomRepository,
      HotelRepository hotelRepository,
      RoomMapper roomMapper,
      RoomReservationRepository reservationRepository,
      @Qualifier("leastBookedStrategy") RoomSelectionStrategy selectionStrategy,
      MostExpensiveStrategy mostExpensiveStrategy,
      RoomPhotoService roomPhotoService) {
    this.roomRepository = roomRepository;
    this.hotelRepository = hotelRepository;
    this.roomMapper = roomMapper;
    this.reservationRepository = reservationRepository;
    this.selectionStrategy = selectionStrategy;
    this.mostExpensiveStrategy = mostExpensiveStrategy;
    this.roomPhotoService = roomPhotoService;
  }

  @Transactional
  public RoomResponse createRoom(RoomRequest request) {

    // Use the hotel-row lock to prevent creating rooms during hotel deletion.
    Hotel hotel =
        hotelRepository
            .findByIdForUpdate(request.hotelId())
            .orElseThrow(() -> new HotelNotFoundException(request.hotelId()));

    String number = request.number().trim();

    Room room = new Room(hotel, number);
    room.changeRoomDetails(
        request.category(), request.capacity(), request.pricePerNight(), request.amenities());

    try {
      Room savedRoom = roomRepository.saveAndFlush(room);
      return roomMapper.toResponse(savedRoom);
    } catch (DataIntegrityViolationException ex) {
      throw new RoomAlreadyExistsException(request.hotelId(), number);
    }
  }

  @Transactional
  public RoomResponse updateAvailability(Long roomId, RoomAvailabilityRequest request) {
    Room room =
        roomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));

    room.setAvailable(request.available());

    return roomMapper.toResponse(room);
  }

  @Transactional
  public RoomResponse updateRoom(Long roomId, RoomUpdateRequest request) {
    Room room =
        roomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));

    String number = request.number().trim();
    Long hotelId = room.getHotel().getId();

    if (roomRepository.existsByHotelIdAndNumberAndIdNot(hotelId, number, roomId)) {
      throw new RoomAlreadyExistsException(hotelId, number);
    }

    room.changeNumber(number);

    try {
      roomRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      throw new RoomAlreadyExistsException(hotelId, number);
    }

    return roomMapper.toResponse(room);
  }

  @Transactional
  public void deleteRoom(Long roomId) {
    Room room =
        roomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));

    if (reservationRepository.existsByRoom_Id(roomId)) {
      throw new ResourceInUseException(
          "ROOM_HAS_RESERVATIONS", "Cannot delete room with reservation history: " + roomId);
    }

    roomPhotoService.removeAllForRoomDeletion(roomId);

    try {
      roomRepository.delete(room);
      roomRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      throw new ResourceInUseException(
          "ROOM_HAS_RESERVATIONS", "Cannot delete room with reservation history: " + roomId);
    }
  }

  @Transactional(readOnly = true)
  public List<RoomResponse> recommendRooms(
      Long hotelId,
      LocalDate checkIn,
      LocalDate checkOut,
      RoomSelectionMode strategy,
      int guestCount) {
    if (hotelId == null || hotelId <= 0) {
      throw new IllegalArgumentException("Invalid hotelId");
    }

    if (checkIn == null
        || checkOut == null
        || checkIn.isBefore(LocalDate.now())
        || !checkOut.isAfter(checkIn)) {
      throw new IllegalArgumentException("Invalid booking dates");
    }
    if (ChronoUnit.DAYS.between(checkIn, checkOut) > 365) {
      throw new IllegalArgumentException("Booking cannot exceed 365 nights");
    }

    if (guestCount < 1) {
      throw new IllegalArgumentException("guestCount must be positive");
    }

    Hotel hotel =
        hotelRepository.findById(hotelId).orElseThrow(() -> new HotelNotFoundException(hotelId));

    List<Room> available =
        roomRepository.findAvailableForPeriod(hotelId, checkIn, checkOut).stream()
            .filter(room -> room.getCapacity() >= guestCount)
            .toList();

    RoomSelectionMode mode = strategy == null ? hotel.getRoomSelectionStrategy() : strategy;

    List<Room> ranked =
        mode == RoomSelectionMode.MOST_EXPENSIVE
            ? mostExpensiveStrategy.rank(available)
            : selectionStrategy.rank(available);

    return ranked.stream().map(roomMapper::toResponse).toList();
  }
}
