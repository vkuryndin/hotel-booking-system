package com.hotelbooking.booking.client;

import com.hotelbooking.booking.exception.HotelServiceUnavailableException;
import com.hotelbooking.booking.security.ServiceTokenProvider;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(classes = HotelServiceClientRetryTest.TestConfig.class)
class HotelServiceClientRetryTest {

    @org.springframework.beans.factory.annotation.Autowired
    private HotelServiceClient client;

    @org.springframework.beans.factory.annotation.Autowired
    private CircuitBreaker breaker;

    @BeforeEach
    void resetBreaker() {
        reset(breaker);
    }

    @Test
    void openCircuitBreakerDoesNotRetry() {
        CallNotPermittedException open =
                CallNotPermittedException.createCallNotPermittedException(
                        io.github.resilience4j.circuitbreaker.CircuitBreaker
                                .ofDefaults("hotelService-test")
                );

        when(breaker.run(any(), any())).thenAnswer(invocation -> {
            Function<Throwable, ?> fallback = invocation.getArgument(1);
            return fallback.apply(open);
        });

        HotelServiceUnavailableException error = assertThrows(
                HotelServiceUnavailableException.class,
                () -> client.getRoom(1L)
        );

        assertInstanceOf(CallNotPermittedException.class, error.getCause());
        verify(breaker, times(1)).run(any(), any());
    }

    @Test
    void transientNetworkFailureStillRetriesTwice() {
        when(breaker.run(any(), any()))
                .thenThrow(new ResourceAccessException("network failure 1"))
                .thenThrow(new ResourceAccessException("network failure 2"))
                .thenThrow(new IllegalArgumentException("stop"));

        assertThrows(IllegalArgumentException.class, () -> client.getRoom(1L));
        verify(breaker, times(3)).run(any(), any());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableResilientMethods
    static class TestConfig {

        @Bean
        CircuitBreaker breaker() {
            return mock(CircuitBreaker.class);
        }

        @Bean
        @SuppressWarnings({"rawtypes", "unchecked"})
        CircuitBreakerFactory<?, ?> breakerFactory(CircuitBreaker breaker) {
            CircuitBreakerFactory factory = mock(CircuitBreakerFactory.class);
            when(factory.create("hotelService")).thenReturn(breaker);
            return factory;
        }

        @Bean
        HotelServiceClient hotelServiceClient(CircuitBreakerFactory<?, ?> factory) {
            return new HotelServiceClient(
                    mock(RestClient.class),
                    mock(ServiceTokenProvider.class),
                    factory
            );
        }
    }
}