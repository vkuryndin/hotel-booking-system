package com.hotelbooking.booking.mapper;

import com.hotelbooking.booking.dto.response.UserResponse;
import com.hotelbooking.booking.entity.AppUser;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AppUserMapper {

    UserResponse toResponse(AppUser user);

}