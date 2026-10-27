package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.repository.BookingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class BookingSagaRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(BookingSagaRecoveryService.class);

    private final BookingRepository bookingRepository;
    private final HotelServiceClient hotelServiceClient;
    private final BookingSagaStateService sagaStateService;

    public BookingSagaRecoveryService(
            BookingRepository bookingRepository,
            HotelServiceClient hotelServiceClient,
            BookingSagaStateService sagaStateService
    ) {
        this.bookingRepository = bookingRepository;
        this.hotelServiceClient = hotelServiceClient;
        this.sagaStateService = sagaStateService;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void recoverPendingBookings() {

        Instant cutoff = Instant.now().minus(Duration.ofMinutes(2));

        var pending = bookingRepository
                .findTop10ByStatusAndCreationOutcomeAndCreatedAtBeforeOrderByCreatedAtAsc(
                        BookingStatus.PENDING,
                        BookingCreationOutcome.PROCESSING,
                        cutoff
                );

        for (Booking booking : pending) {
            try {
                hotelServiceClient.releaseRoom(
                        booking.getRoomId(),
                        booking.getReservationId()
                );

                sagaStateService.cancelCreation(
                        booking.getId(),
                        BookingCreationOutcome.FAILED
                );

                log.info(
                        "Saga recovered: bookingId={}, reservationId={}",
                        booking.getId(),
                        booking.getReservationId()
                );

            } catch (Exception ex) {
                log.warn(
                        "Saga recovery failed: bookingId={}",
                        booking.getId(),
                        ex
                );
            }
        }
    }
}