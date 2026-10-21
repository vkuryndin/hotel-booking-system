package com.hotelbooking.hotel.mapper;

import com.hotelbooking.hotel.dto.response.HotelResponse;
import com.hotelbooking.hotel.entity.Hotel;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface HotelMapper {

    HotelResponse toResponse(Hotel hotel);
}