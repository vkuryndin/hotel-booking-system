package com.hotelbooking.hotel.dto.response;

import com.hotelbooking.hotel.entity.HotelPhoto;

public record HotelPhotoResponse(
        Long id,
        Long hotelId,
        String url,
        boolean primary,
        int sortOrder
) {
    public static HotelPhotoResponse from(HotelPhoto photo) {
        Long hotelId = photo.getHotel().getId();

        return new HotelPhotoResponse(
                photo.getId(),
                hotelId,
                "/api/hotels/" + hotelId
                        + "/photos/" + photo.getId() + "/file",
                photo.isPrimaryPhoto(),
                photo.getSortOrder()
        );
    }
}