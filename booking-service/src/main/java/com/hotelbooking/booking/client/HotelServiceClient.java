package com.hotelbooking.booking.client;

import com.hotelbooking.booking.dto.client.HotelRoomResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HotelServiceClient {

    private final RestClient hotelRestClient;

    public HotelServiceClient(RestClient hotelRestClient) {
        this.hotelRestClient = hotelRestClient;
    }

    public HotelRoomResponse getRoom(Long roomId) {
        HotelRoomResponse room = hotelRestClient.get()
                .uri("/api/rooms/{id}", roomId)
                .retrieve()
                .body(HotelRoomResponse.class);

        if (room == null) {
            throw new IllegalStateException(
                    "Empty response from Hotel Service"
            );
        }

        return room;
    }
}