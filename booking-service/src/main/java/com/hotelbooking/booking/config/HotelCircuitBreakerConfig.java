package com.hotelbooking.booking.config;

import com.hotelbooking.booking.exception.HotelServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

@Configuration
public class HotelCircuitBreakerConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory>
    hotelServiceCircuitBreaker() {

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(
                        CircuitBreakerConfig.SlidingWindowType.COUNT_BASED
                )
                .slidingWindowSize(6)
                .minimumNumberOfCalls(6)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(2)

                .recordException(error ->
                        error instanceof ResourceAccessException
                                || error instanceof HttpServerErrorException
                                || error instanceof HotelServiceUnavailableException
                                || (error instanceof IllegalStateException
                                && error.getMessage() != null
                                && error.getMessage().startsWith(
                                "No instances available for "
                        ))
                )

                .ignoreExceptions(HttpClientErrorException.class)
                .build();

        return factory -> factory.configure(
                builder -> builder.circuitBreakerConfig(config),
                "hotelService"
        );
    }
}