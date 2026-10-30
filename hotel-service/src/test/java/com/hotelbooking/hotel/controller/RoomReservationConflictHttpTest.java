package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.exception.GlobalExceptionHandler;
import com.hotelbooking.hotel.exception.RoomReservationConflictException;
import com.hotelbooking.hotel.service.RoomReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RoomReservationConflictHttpTest {

    @Test
    void duplicateReservationIdReturnsHttp409AndStableCode() throws Exception {
        RoomReservationService reservationService = mock(RoomReservationService.class);
        UUID id = UUID.fromString("11444444-4444-4444-4444-444444444444");
        LocalDate checkIn = LocalDate.of(2055, 1, 5);
        LocalDate checkOut = LocalDate.of(2055, 1, 7);

        when(reservationService.reserve(eq(id), eq(1L), eq(checkIn), eq(checkOut), eq(1)))
                .thenThrow(new RoomReservationConflictException(
                        "RESERVATION_ID_CONFLICT", "Reservation ID already used: " + id
                ));

        MockMvc mvc = standaloneSetup(new RoomReservationController(reservationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/rooms/1/confirm-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": "11444444-4444-4444-4444-444444444444",
                                  "checkIn": "2055-01-05",
                                  "checkOut": "2055-01-07",
                                  "guestCount": 1
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_ID_CONFLICT"))
                .andExpect(jsonPath("$.status").value(409));
    }
}
