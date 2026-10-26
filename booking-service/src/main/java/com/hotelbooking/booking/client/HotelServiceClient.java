package com.hotelbooking.booking.client;

import com.hotelbooking.booking.dto.client.HotelRoomResponse;
import com.hotelbooking.booking.exception.HotelServiceUnavailableException;
import com.hotelbooking.booking.exception.RoomNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.hotelbooking.booking.dto.client.HotelReservationRequest;
import com.hotelbooking.booking.dto.client.HotelReservationResponse;
import com.hotelbooking.booking.dto.client.HotelReservationReleaseRequest;
import com.hotelbooking.booking.security.ServiceTokenProvider;

import java.time.LocalDate;
import java.util.UUID;
import java.util.Objects;

@Component
public class HotelServiceClient {

    private final RestClient hotelRestClient;
    private final ServiceTokenProvider serviceTokenProvider;


    public HotelServiceClient(
            RestClient hotelRestClient,
            ServiceTokenProvider serviceTokenProvider
    ) {
        this.hotelRestClient = hotelRestClient;
        this.serviceTokenProvider = serviceTokenProvider;
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
    public HotelReservationResponse reserveRoom(
            Long roomId,
            UUID reservationId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        HotelReservationResponse response = hotelRestClient.post()
                .uri("/api/rooms/{id}/confirm-availability", roomId)
                .headers(headers -> headers.setBearerAuth(
                        serviceTokenProvider.generateToken()
                ))
                .body(new HotelReservationRequest(
                        reservationId, checkIn, checkOut
                ))
                .retrieve()
                .body(HotelReservationResponse.class);

        return Objects.requireNonNull(
                response, "Empty reservation response from Hotel Service"
        );
    }

    public HotelReservationResponse releaseRoom(
            Long roomId,
            UUID reservationId
    ) {
        HotelReservationResponse response = hotelRestClient.post()
                .uri("/api/rooms/{id}/release", roomId)
                .headers(headers -> headers.setBearerAuth(
                        serviceTokenProvider.generateToken()
                ))
                .body(new HotelReservationReleaseRequest(reservationId))
                .retrieve()
                .body(HotelReservationResponse.class);

        return Objects.requireNonNull(
                response, "Empty release response from Hotel Service"
        );
    }
}