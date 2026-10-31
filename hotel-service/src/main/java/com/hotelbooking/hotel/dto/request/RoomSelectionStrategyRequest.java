package com.hotelbooking.hotel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.hotel.strategy.RoomSelectionMode;
import jakarta.validation.constraints.NotNull;

public record RoomSelectionStrategyRequest(@Schema(description = "Стратегия подбора: LEAST_BOOKED или MOST_EXPENSIVE.") @NotNull RoomSelectionMode strategy) {}
