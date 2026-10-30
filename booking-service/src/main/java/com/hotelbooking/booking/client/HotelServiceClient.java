
package com.hotelbooking.booking.client;

import com.hotelbooking.booking.dto.client.HotelRoomResponse;
import com.hotelbooking.booking.dto.client.HotelReservationRequest;
import com.hotelbooking.booking.dto.client.HotelReservationResponse;
import com.hotelbooking.booking.dto.client.HotelReservationReleaseRequest;
import com.hotelbooking.booking.dto.request.RoomSelectionMode;
import com.hotelbooking.booking.exception.HotelServiceUnavailableException;
import com.hotelbooking.booking.exception.RoomNotFoundException;
import com.hotelbooking.booking.exception.HotelNotFoundException;
import com.hotelbooking.booking.security.ServiceTokenProvider;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Retryable(
        includes = {
                ResourceAccessException.class,
                HttpServerErrorException.BadGateway.class,
                HttpServerErrorException.ServiceUnavailable.class,
                HttpServerErrorException.GatewayTimeout.class,
                HotelServiceUnavailableException.class
        },
        excludes = {
                CallNotPermittedException.class
        },
        maxRetries = 2,
        delay = 200,
        multiplier = 2.0,
        maxDelay = 1000
)
@Component
public class HotelServiceClient {

    private final RestClient hotelRestClient;
    private final ServiceTokenProvider serviceTokenProvider;

    private static final Logger log =
            LoggerFactory.getLogger(HotelServiceClient.class);

    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public HotelServiceClient(
            RestClient hotelRestClient,
            ServiceTokenProvider serviceTokenProvider,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.hotelRestClient = hotelRestClient;
        this.serviceTokenProvider = serviceTokenProvider;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public HotelRoomResponse getRoom(Long roomId) {
        try {
            return withCircuitBreaker(() -> {

                HotelRoomResponse room = hotelRestClient.get()
                        .uri("/api/rooms/{id}", roomId)
                        .headers(headers -> headers.setBearerAuth(
                                serviceTokenProvider.generateToken()
                        ))
                        .retrieve()
                        .body(HotelRoomResponse.class);

                if (room == null) {
                    throw new HotelServiceUnavailableException(
                            "Empty response from Hotel Service"
                    );
                }

                return room;
            });

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
            LocalDate checkOut,
            int guestCount
    ) {
        return withCircuitBreaker(() -> {

            HotelReservationResponse response = hotelRestClient.post()
                    .uri("/api/rooms/{id}/confirm-availability", roomId)
                    .headers(headers -> headers.setBearerAuth(
                            serviceTokenProvider.generateToken()
                    ))
                    .body(new HotelReservationRequest(
                            reservationId,
                            checkIn,
                            checkOut,
                            guestCount
                    ))
                    .retrieve()
                    .body(HotelReservationResponse.class);

            return Objects.requireNonNull(
                    response,
                    "Empty reservation response from Hotel Service"
            );
        });
    }

    // Обычная отмена подтверждённого бронирования
    // При освобождении активной брони timesBooked уменьшается ровно один раз
    public HotelReservationResponse releaseRoom(
            Long roomId,
            UUID reservationId
    ) {
        return sendRelease(roomId, reservationId, false);
    }

    // Компенсация неудачной Saga
    // При компенсации активной резервации timesBooked также уменьшается ровно один раз
    public HotelReservationResponse compensateRoom(
            Long roomId,
            UUID reservationId
    ) {
        return sendRelease(roomId, reservationId, true);
    }

    // Общий метод освобождения номера
    private HotelReservationResponse sendRelease(
            Long roomId,
            UUID reservationId,
            boolean compensation
    ) {
        return withCircuitBreaker(() -> {

            HotelReservationResponse response = hotelRestClient.post()
                    .uri("/api/rooms/{id}/release", roomId)
                    .headers(headers -> headers.setBearerAuth(
                            serviceTokenProvider.generateToken()
                    ))
                    .body(new HotelReservationReleaseRequest(
                            reservationId,
                            compensation
                    ))
                    .retrieve()
                    .body(HotelReservationResponse.class);

            return Objects.requireNonNull(
                    response,
                    "Empty release response from Hotel Service"
            );
        });
    }

    public List<HotelRoomResponse> recommendRooms(
            Long hotelId,
            LocalDate checkIn,
            LocalDate checkOut,
            RoomSelectionMode strategy,
            int guestCount
    ) {
        try {
            return withCircuitBreaker(() -> {

                List<HotelRoomResponse> response = hotelRestClient.get()
                        .uri(builder -> builder
                                .path("/api/rooms/recommend")
                                .queryParam("hotelId", hotelId)
                                .queryParam("checkIn", checkIn)
                                .queryParam("checkOut", checkOut)
                                .queryParamIfPresent("strategy", java.util.Optional.ofNullable(strategy))
                                .queryParam("guestCount", guestCount)
                                .build())
                        .headers(headers -> headers.setBearerAuth(
                                serviceTokenProvider.generateToken()
                        ))
                        .retrieve()
                        .body(new ParameterizedTypeReference<
                                List<HotelRoomResponse>>() {});

                if (response == null) {
                    throw new HotelServiceUnavailableException(
                            "Empty recommendations from Hotel Service"
                    );
                }

                return response;
            });

        } catch (HttpClientErrorException.NotFound ex) {
            throw new HotelNotFoundException(hotelId);
        }
    }


    private <T> T withCircuitBreaker(Supplier<T> operation) {

        return circuitBreakerFactory.create("hotelService").run(
                operation,
                error -> {

                    if (error instanceof CallNotPermittedException) {
                        log.warn(
                                "Hotel Service Circuit Breaker is OPEN"
                        );
                        throw new HotelServiceUnavailableException(error);
                    }

                    if (error instanceof RuntimeException runtimeError) {
                        throw runtimeError;
                    }

                    throw new HotelServiceUnavailableException(error);
                }
        );
    }
}
