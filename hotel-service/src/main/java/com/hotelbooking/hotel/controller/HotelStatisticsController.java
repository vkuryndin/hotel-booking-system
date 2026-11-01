package com.hotelbooking.hotel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.hotel.dto.response.HotelStatisticsResponse;
import com.hotelbooking.hotel.service.HotelStatisticsService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Статистика отелей", description = "Административная статистика отеля.")
@RestController
@RequestMapping("/api/hotels")
public class HotelStatisticsController {

    private final HotelStatisticsService statisticsService;

    public HotelStatisticsController(
            HotelStatisticsService statisticsService
    ) {
        this.statisticsService = statisticsService;
    }

    @Operation(summary = "Получить статистику отеля (ADMIN)", description = "Возвращает агрегированную статистику номеров и бронирований выбранного отеля.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Статистика отеля.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный отель, номер или фотография не найдены.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
    })
    @GetMapping("/{hotelId}/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelStatisticsResponse getStatistics(
            @Parameter(description = "Идентификатор отеля.") @PathVariable Long hotelId
    ) {
        return statisticsService.getStatistics(hotelId);
    }
}