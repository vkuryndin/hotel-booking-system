package com.hotelbooking.hotel.dto.request;

import com.hotelbooking.hotel.entity.RoomAmenity;
import com.hotelbooking.hotel.entity.RoomCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record RoomDetailsRequest(
    @Schema(description = "Категория номера.") RoomCategory category,
    @Schema(description = "Вместимость — положительное целое число.") @Positive Integer capacity,
    @Schema(description = "Цена за ночь (неотрицательное значение).") @DecimalMin("0.00")
        BigDecimal pricePerNight,
    @Schema(description = "Удобства номера из перечисления RoomAmenity.")
        Set<RoomAmenity> amenities) {
  public RoomDetailsRequest {
    if (amenities != null) {
      amenities = Collections.unmodifiableSet(new LinkedHashSet<>(amenities));
    }
  }

  @Override
  public Set<RoomAmenity> amenities() {
    return amenities == null ? null : Collections.unmodifiableSet(amenities);
  }
}
