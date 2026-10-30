package com.hotelbooking.hotel.dto.request;

import com.hotelbooking.hotel.strategy.RoomSelectionMode;
import jakarta.validation.constraints.NotNull;

public record RoomSelectionStrategyRequest(@NotNull RoomSelectionMode strategy) {}
