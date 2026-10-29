package com.hotelbooking.booking.config;

import com.hotelbooking.booking.exception.HotelServiceUnavailableException;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class RestClientConfigTest {

    @Test
    void transportIllegalStateBecomesServiceUnavailable() {
        RestClient client = new RestClientConfig().hotelRestClient(
                RestClient.builder()
                        .requestInterceptor((request, body, execution) -> {
                            throw new IllegalStateException(
                                    "arbitrary transport failure"
                            );
                        })
        );

        HotelServiceUnavailableException ex = assertThrows(
                HotelServiceUnavailableException.class,
                () -> client.get()
                        .uri("/api/rooms/1")
                        .retrieve()
                        .toBodilessEntity()
        );

        assertInstanceOf(
                IllegalStateException.class,
                ex.getCause()
        );
    }

    @Test
    void unrelatedTransportArgumentErrorIsNotConverted() {
        RestClient client = new RestClientConfig().hotelRestClient(
                RestClient.builder()
                        .requestInterceptor((request, body, execution) -> {
                            throw new IllegalArgumentException(
                                    "invalid request"
                            );
                        })
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> client.get()
                        .uri("/api/rooms/1")
                        .retrieve()
                        .toBodilessEntity()
        );
    }
}