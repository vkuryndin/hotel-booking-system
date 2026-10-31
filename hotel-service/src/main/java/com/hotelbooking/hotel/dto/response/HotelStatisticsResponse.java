package com.hotelbooking.hotel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record HotelStatisticsResponse(
        @Schema(description = "Идентификатор отеля.") Long hotelId,
        @Schema(description = "Дата расчёта показателей.") LocalDate statisticsDate,
        @Schema(description = "Всего номеров в отеле.") long totalRooms,
        @Schema(description = "Номера с признаком available=true.") long availableRooms,
        @Schema(description = "Номера с признаком available=false.") long unavailableRooms,
        @Schema(description = "Номера, занятые сегодня по бронированиям.") long occupiedRoomsToday,
        @Schema(description = "Всего записей резервирования.") long totalReservations,
        @Schema(description = "Активные резервирования.") long reservedReservations,
        @Schema(description = "Освобождённые резервирования.") long releasedReservations,
        @Schema(description = "Суммарный счётчик бронирований номеров.") long totalTimesBooked,
        @Schema(description = "Компенсационные отметки об освобождении до резервирования.") long tombstoneReservations
) {
}