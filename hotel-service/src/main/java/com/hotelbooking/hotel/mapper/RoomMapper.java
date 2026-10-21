package com.hotelbooking.hotel.mapper;

import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.entity.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoomMapper {

    @Mapping(target = "hotelId", source = "hotel.id")
    RoomResponse toResponse(Room room);
}