package com.hotelbooking.hotel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.hotel.entity.HotelPhoto;

public record HotelPhotoResponse(
        @Schema(description = "Идентификатор фотографии.") Long id,
        @Schema(description = "Идентификатор отеля.") Long hotelId,
        @Schema(description = "Путь для получения файла изображения через Gateway.") String url,
        @Schema(description = "Является ли фотография главной.") boolean primary,
        @Schema(description = "Позиция в галерее.") int sortOrder
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