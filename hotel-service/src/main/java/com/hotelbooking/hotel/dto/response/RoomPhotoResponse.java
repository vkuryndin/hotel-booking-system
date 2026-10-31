package com.hotelbooking.hotel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.hotel.entity.RoomPhoto;

public record RoomPhotoResponse(
        @Schema(description = "Идентификатор фотографии.") Long id,
        @Schema(description = "Идентификатор номера.") Long roomId,
        @Schema(description = "Путь к файлу фотографии через Gateway.") String url,
        @Schema(description = "Является ли фотография главной.") boolean primary,
        @Schema(description = "Позиция в галерее.") int sortOrder
) {
    public static RoomPhotoResponse from(RoomPhoto photo) {
        Long roomId = photo.getRoom().getId();

        return new RoomPhotoResponse(
                photo.getId(),
                roomId,
                "/api/rooms/" + roomId
                        + "/photos/" + photo.getId() + "/file",
                photo.isPrimaryPhoto(),
                photo.getSortOrder()
        );
    }
}