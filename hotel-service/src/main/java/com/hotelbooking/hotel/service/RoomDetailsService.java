package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.request.RoomDetailsRequest;
import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.RoomRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomDetailsService {

    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;

    public RoomDetailsService(
            RoomRepository roomRepository,
            RoomMapper roomMapper
    ) {
        this.roomRepository = roomRepository;
        this.roomMapper = roomMapper;
    }

    @Transactional
    public RoomResponse update(
            Long roomId,
            RoomDetailsRequest request
    ) {
        if (request.category() == null
                && request.capacity() == null
                && request.pricePerNight() == null
                && request.amenities() == null) {
            throw new IllegalArgumentException("No fields to update");
        }

        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        room.changeRoomDetails(
                request.category(),
                request.capacity(),
                request.pricePerNight(),
                request.amenities()
        );

        return roomMapper.toResponse(room);
    }
}