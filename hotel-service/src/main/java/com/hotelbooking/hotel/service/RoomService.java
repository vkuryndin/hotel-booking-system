package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.request.RoomAvailabilityRequest;
import com.hotelbooking.hotel.dto.request.RoomRequest;
import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.exception.RoomAlreadyExistsException;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hotelbooking.hotel.strategy.RoomSelectionStrategy;
import java.time.LocalDate;


import java.util.List;
import com.hotelbooking.hotel.dto.request.RoomUpdateRequest;
import com.hotelbooking.hotel.exception.ResourceInUseException;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomMapper roomMapper;
    private final RoomReservationRepository reservationRepository;
    private final RoomSelectionStrategy selectionStrategy;

    public RoomService(
            RoomRepository roomRepository,
            HotelRepository hotelRepository,
            RoomMapper roomMapper,
            RoomReservationRepository reservationRepository,
            RoomSelectionStrategy selectionStrategy
    ) {
        this.roomRepository = roomRepository;
        this.hotelRepository = hotelRepository;
        this.roomMapper = roomMapper;
        this.reservationRepository = reservationRepository;
        this.selectionStrategy = selectionStrategy;
    }

    @Transactional
    public RoomResponse createRoom(RoomRequest request) {

        Hotel hotel = hotelRepository.findById(request.hotelId())
                .orElseThrow(() ->
                        new HotelNotFoundException(request.hotelId()));

        String number = request.number().trim();

        Room room = new Room(hotel, number);

        try {
            Room savedRoom = roomRepository.saveAndFlush(room);
            return roomMapper.toResponse(savedRoom);
        } catch (DataIntegrityViolationException ex) {
            throw new RoomAlreadyExistsException(
                    request.hotelId(), number);
        }
    }
    @Transactional(readOnly = true)
    public List<RoomResponse> getRoomsByHotelId(Long hotelId) {

        if (!hotelRepository.existsById(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }

        return roomRepository.findByHotelId(hotelId)
                .stream()
                .map(roomMapper::toResponse)
                .toList();
    }
    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long roomId) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        return roomMapper.toResponse(room);
    }
    @Transactional
    public RoomResponse updateAvailability(
            Long roomId,
            RoomAvailabilityRequest request
    ) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        room.setAvailable(request.available());

        return roomMapper.toResponse(room);
    }
    @Transactional(readOnly = true)
    public List<RoomResponse> getAvailableRoomsByHotelId(Long hotelId) {

        if (!hotelRepository.existsById(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }

        return roomRepository.findByHotelIdAndAvailableTrue(hotelId)
                .stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    @Transactional
    public RoomResponse updateRoom(
            Long roomId,
            RoomUpdateRequest request
    ) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        String number = request.number().trim();
        Long hotelId = room.getHotel().getId();

        if (roomRepository.existsByHotelIdAndNumberAndIdNot(
                hotelId, number, roomId)) {
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
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (reservationRepository.existsByRoom_Id(roomId)) {
            throw new ResourceInUseException(
                    "ROOM_HAS_RESERVATIONS",
                    "Cannot delete room with reservation history: " + roomId
            );
        }

        try {
            roomRepository.delete(room);
            roomRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceInUseException(
                    "ROOM_HAS_RESERVATIONS",
                    "Cannot delete room with reservation history: " + roomId
            );
        }
    }
    @Transactional(readOnly = true)
    public List<RoomResponse> recommendRooms(
            Long hotelId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        if (hotelId == null || hotelId <= 0) {
            throw new IllegalArgumentException("Invalid hotelId");
        }

        if (checkIn == null || checkOut == null
                || checkIn.isBefore(LocalDate.now())
                || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Invalid booking dates");
        }

        if (!hotelRepository.existsById(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }

        List<Room> rooms = roomRepository.findAvailableForPeriod(
                hotelId, checkIn, checkOut
        );

        return selectionStrategy.rank(rooms)
                .stream()
                .map(roomMapper::toResponse)
                .toList();
    }

}