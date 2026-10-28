package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.response.HotelStatisticsResponse;
import com.hotelbooking.hotel.service.HotelStatisticsService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hotels")
public class HotelStatisticsController {

    private final HotelStatisticsService statisticsService;

    public HotelStatisticsController(
            HotelStatisticsService statisticsService
    ) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/{hotelId}/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelStatisticsResponse getStatistics(
            @PathVariable Long hotelId
    ) {
        return statisticsService.getStatistics(hotelId);
    }
}