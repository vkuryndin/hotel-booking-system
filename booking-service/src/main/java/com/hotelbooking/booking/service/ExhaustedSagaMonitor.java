package com.hotelbooking.booking.service;

import com.hotelbooking.booking.repository.BookingRepository;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.entity.BookingCreationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

/** Alerts on unresolved Sagas; never changes their state automatically after the retry limit. */
@Component
public class ExhaustedSagaMonitor {
    private static final Logger log = LoggerFactory.getLogger(ExhaustedSagaMonitor.class);
    private final BookingRepository repository;

    public ExhaustedSagaMonitor(BookingRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 300000)
    public void reportExhausted() {
        var exhausted = repository.findExhaustedRecovery(
                BookingStatus.PENDING, BookingStatus.CANCELLING,
                List.of(BookingCreationOutcome.PROCESSING, BookingCreationOutcome.RECOVERING),
                BookingSagaStateService.MAX_RECOVERY_ATTEMPTS, PageRequest.of(0, 100));
        if (!exhausted.isEmpty()) {
            log.error("SAGA_REQUIRES_ATTENTION: {} exhausted bookings (up to 100). "
                    + "Use GET /api/bookings/recovery/exhausted and POST /api/bookings/{id}/recovery/retry", exhausted.size());
        }
    }
}
