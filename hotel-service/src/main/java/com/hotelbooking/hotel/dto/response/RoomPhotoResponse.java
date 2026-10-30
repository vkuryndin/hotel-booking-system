package com.hotelbooking.hotel.dto.response;

import com.hotelbooking.hotel.entity.RoomPhoto;

public record RoomPhotoResponse(
        Long id,
        Long roomId,
        String url,
        boolean primary,
        int sortOrder
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