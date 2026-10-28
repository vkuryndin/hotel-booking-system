package com.hotelbooking.booking.service;
import java.util.List;
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
                .findTop10ByStatusAndCreationOutcomeInAndCreatedAtBeforeOrderByCreatedAtAsc(
                        BookingStatus.PENDING,
                        List.of(
                                BookingCreationOutcome.PROCESSING,
                                BookingCreationOutcome.RECOVERING
                        ),
                        cutoff
                );

        for (Booking booking : pending) {
            try {
                var candidate = sagaStateService.prepareRecovery(
                        booking.getId(), cutoff
                );

                if (candidate.isEmpty()) {
                    continue;
                }

                var recovery = candidate.get();

                hotelServiceClient.compensateRoom(
                        recovery.roomId(),
                        recovery.reservationId()
                );

                sagaStateService.cancelCreation(
                        recovery.bookingId(),
                        BookingCreationOutcome.FAILED
                );

                log.info(
                        "Saga recovered: bookingId={}, reservationId={}",
                        recovery.bookingId(),
                        recovery.reservationId()
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
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void recoverCancellingBookings() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(2));

        var cancelling = bookingRepository
                .findTop10ByStatusAndCancellationRequestedAtBeforeOrderByCancellationRequestedAtAsc(
                        BookingStatus.CANCELLING,
                        cutoff
                );

        for (Booking booking : cancelling) {
            try {
                var candidate = sagaStateService.prepareCancellationRecovery(
                        booking.getId(),
                        cutoff
                );

                if (candidate.isEmpty()) {
                    continue;
                }

                var recovery = candidate.get();

                hotelServiceClient.releaseRoom(
                        recovery.roomId(),
                        recovery.reservationId()
                );

                sagaStateService.finishCancellation(recovery.bookingId());

                log.info(
                        "Cancellation recovered: bookingId={}",
                        recovery.bookingId()
                );

            } catch (Exception ex) {
                log.warn(
                        "Cancellation recovery failed: bookingId={}",
                        booking.getId(),
                        ex
                );
            }
        }
    }
}