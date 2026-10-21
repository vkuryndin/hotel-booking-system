package com.hotelbooking.hotel.service;

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

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomMapper roomMapper;

    public RoomService(
            RoomRepository roomRepository,
            HotelRepository hotelRepository,
            RoomMapper roomMapper
    ) {
        this.roomRepository = roomRepository;
        this.hotelRepository = hotelRepository;
        this.roomMapper = roomMapper;
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
}