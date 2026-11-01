package com.hotelbooking.booking.dto.client;

import java.time.LocalDate;
import java.util.UUID;

public record HotelReservationRequest(
    UUID reservationId, LocalDate checkIn, LocalDate checkOut, Integer guestCount) {
  public HotelReservationRequest {
    if (guestCount == null) {
      guestCount = 1;
    }
  }

  public HotelReservationRequest(UUID reservationId, LocalDate checkIn, LocalDate checkOut) {
    this(reservationId, checkIn, checkOut, 1);
  }
}
