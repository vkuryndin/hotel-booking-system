package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.repository.BookingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class BookingSagaRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(BookingSagaRecoveryService.class);

    // Размер одной выборки из БД.
    private static final int PAGE_SIZE = 10;

    // Максимум кандидатов за один цикл каждого процесса.
    private static final int MAX_CANDIDATES_PER_CYCLE = 100;

    // Мягкий лимит времени: уже начатый HTTP-вызов
    // не прерываем.
    private static final Duration MAX_CYCLE_DURATION =
            Duration.ofSeconds(20);

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

        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofMinutes(2));

        long deadline = System.nanoTime()
                + MAX_CYCLE_DURATION.toNanos();

        // Начальный курсор.
        Instant cursorTime = Instant.EPOCH;
        Long cursorId = 0L;

        int scanned = 0;

        // Обходим несколько пакетов, но не более 100 записей
        // и не более 20 секунд до начала следующей операции.
        recoveryLoop:
        while (scanned < MAX_CANDIDATES_PER_CYCLE
                && System.nanoTime() < deadline) {

            int pageSize = Math.min(
                    PAGE_SIZE,
                    MAX_CANDIDATES_PER_CYCLE - scanned
            );

            var pending = bookingRepository
                    .findPendingRecoveryCandidates(
                            BookingStatus.PENDING,
                            List.of(
                                    BookingCreationOutcome.PROCESSING,
                                    BookingCreationOutcome.RECOVERING
                            ),
                            cutoff,
                            now,
                            BookingSagaStateService.MAX_RECOVERY_ATTEMPTS,
                            cursorTime,
                            cursorId,
                            PageRequest.of(0, pageSize)
                    );

            if (pending.isEmpty()) {
                break;
            }

            for (Booking booking : pending) {

                if (System.nanoTime() >= deadline) {
                    break recoveryLoop;
                }

                scanned++;

                // Сдвигаем курсор независимо от результата.
                // Это предотвращает повторное взятие одной
                // проблемной записи в этом же цикле.
                cursorTime = booking.getSagaUpdatedAt();
                cursorId = booking.getId();

                try {
                    var candidate = sagaStateService.prepareRecovery(
                            booking.getId(),
                            cutoff
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

        if (scanned > 0) {
            log.info(
                    "Pending Recovery cycle finished: scanned={}",
                    scanned
            );
        }
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void recoverCancellingBookings() {

        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofMinutes(2));

        long deadline = System.nanoTime()
                + MAX_CYCLE_DURATION.toNanos();

        Instant cursorTime = Instant.EPOCH;
        Long cursorId = 0L;

        int scanned = 0;

        recoveryLoop:
        while (scanned < MAX_CANDIDATES_PER_CYCLE
                && System.nanoTime() < deadline) {

            int pageSize = Math.min(
                    PAGE_SIZE,
                    MAX_CANDIDATES_PER_CYCLE - scanned
            );

            var cancelling = bookingRepository
                    .findCancellationRecoveryCandidates(
                            BookingStatus.CANCELLING,
                            cutoff,
                            now,
                            BookingSagaStateService.MAX_RECOVERY_ATTEMPTS,
                            cursorTime,
                            cursorId,
                            PageRequest.of(0, pageSize)
                    );

            if (cancelling.isEmpty()) {
                break;
            }

            for (Booking booking : cancelling) {

                if (System.nanoTime() >= deadline) {
                    break recoveryLoop;
                }

                scanned++;

                cursorTime = booking.getCancellationRequestedAt();
                cursorId = booking.getId();

                try {
                    var candidate =
                            sagaStateService.prepareCancellationRecovery(
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

                    sagaStateService.finishCancellation(
                            recovery.bookingId()
                    );

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

        if (scanned > 0) {
            log.info(
                    "Cancellation Recovery cycle finished: scanned={}",
                    scanned
            );
        }
    }
}