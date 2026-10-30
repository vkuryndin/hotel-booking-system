package com.hotelbooking.booking.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BookingRequestValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    private BookingRequest booking(LocalDate checkIn, LocalDate checkOut, Integer guests) {
        return new BookingRequest(1L, null, false, checkIn, checkOut, guests, null);
    }

    private Set<String> invalidFields(BookingRequest request) {
        return VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void zeroGuestsAreRejected() {
        assertTrue(invalidFields(booking(LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(6), 0)).contains("guestCount"));
    }

    @Test
    void guestCountAboveTwentyIsRejected() {
        assertTrue(invalidFields(booking(LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(6), 21)).contains("guestCount"));
    }

    @Test
    void oneAndTwentyGuestsAreAccepted() {
        LocalDate in = LocalDate.now().plusDays(5);
        LocalDate out = in.plusDays(2);
        assertTrue(invalidFields(booking(in, out, 1)).isEmpty());
        assertTrue(invalidFields(booking(in, out, 20)).isEmpty());
    }

    @Test
    void omittedGuestCountDefaultsToOne() {
        BookingRequest request = booking(LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(6), null);
        assertEquals(1, request.guestCount());
        assertTrue(invalidFields(request).isEmpty());
    }

    @Test
    void checkoutTodayOrYesterdayIsRejected() {
        LocalDate in = LocalDate.now().plusDays(2);
        assertTrue(invalidFields(booking(in, LocalDate.now(), 1)).contains("checkOut"));
        assertTrue(invalidFields(booking(in, LocalDate.now().minusDays(1), 1))
                .contains("checkOut"));
    }

    @Test
    void omittedCheckoutIsRejected() {
        assertTrue(invalidFields(booking(LocalDate.now().plusDays(5), null, 1))
                .contains("checkOut"));
    }

    @Test
    void pastCheckinIsStillRejected() {
        assertTrue(invalidFields(booking(LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1), 1)).contains("checkIn"));
    }

    @Test
    void todayCheckinTomorrowCheckoutAreAccepted() {
        assertTrue(invalidFields(booking(LocalDate.now(),
                LocalDate.now().plusDays(1), 1)).isEmpty());
    }
}
