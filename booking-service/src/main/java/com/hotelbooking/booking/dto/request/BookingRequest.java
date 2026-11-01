package com.hotelbooking.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record BookingRequest(
    @Schema(description = "ID номера при ручном выборе (autoSelect=false).") Long roomId,
    @Schema(description = "ID отеля для автоматического подбора (autoSelect=true).") Long hotelId,
    @Schema(description = "Автоматически выбрать подходящий номер в отеле (по умолчанию false).")
        Boolean autoSelect,
    @Schema(description = "Дата заезда, сегодня или позже (YYYY-MM-DD).") @NotNull @FutureOrPresent
        LocalDate checkIn,
    @Schema(description = "Дата выезда позже даты заезда (YYYY-MM-DD).") @NotNull @Future
        LocalDate checkOut,
    @Schema(description = "Количество гостей: от 1 до 20; по умолчанию 1.") @Positive @Max(20)
        Integer guestCount,
    @Schema(description = "Стратегия автоматического подбора (если не указана — настройка отеля).")
        RoomSelectionMode strategy) {
  public BookingRequest {
    autoSelect = Boolean.TRUE.equals(autoSelect);
    if (guestCount == null) {
      guestCount = 1;
    }
  }

  public String fingerprint() {
    if (Boolean.TRUE.equals(autoSelect)) {
      return "AUTO:"
          + hotelId
          + ":"
          + checkIn
          + ":"
          + checkOut
          + ":"
          + guestCount
          + ":"
          + (strategy == null ? "HOTEL_DEFAULT" : strategy.name());
    }

    return "ROOM:" + roomId + ":" + checkIn + ":" + checkOut + ":" + guestCount;
  }
}
