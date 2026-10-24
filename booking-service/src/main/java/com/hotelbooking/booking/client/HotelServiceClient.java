package com.hotelbooking.booking.client;

import com.hotelbooking.booking.dto.client.HotelRoomResponse;
import com.hotelbooking.booking.exception.HotelServiceUnavailableException;
import com.hotelbooking.booking.exception.RoomNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class HotelServiceClient {

    private final RestClient hotelRestClient;

    public HotelServiceClient(RestClient hotelRestClient) {
        this.hotelRestClient = hotelRestClient;
    }

    public HotelRoomResponse getRoom(Long roomId) {
        try {
            HotelRoomResponse room = hotelRestClient.get()
                    .uri("/api/rooms/{id}", roomId)
                    .retrieve()
                    .body(HotelRoomResponse.class);

            if (room == null) {
                throw new HotelServiceUnavailableException(
                        "Empty response from Hotel Service"
                );
            }

            return room;

        } catch (HttpClientErrorException.NotFound ex) {
            throw new RoomNotFoundException(roomId);

        } catch (HttpServerErrorException | ResourceAccessException ex) {
            throw new HotelServiceUnavailableException(ex);
        }
    }
}