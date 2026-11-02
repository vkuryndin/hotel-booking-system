package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.request.HotelPresentationRequest;
import com.hotelbooking.hotel.dto.response.HotelResponse;
import com.hotelbooking.hotel.service.HotelPresentationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Оформление отелей", description = "Оформление, удобства и время заезда/выезда отеля.")
@RestController
@RequestMapping("/api/hotels")
public class HotelPresentationController {

  private final HotelPresentationService service;

  public HotelPresentationController(HotelPresentationService service) {
    this.service = service;
  }

  @Operation(
      summary = "Обновить оформление отеля (ADMIN)",
      description =
          "Частично изменяет описание, рейтинг (0–5), удобства и стандартное время заезда/выезда.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Обновлённые данные отеля.",
        useReturnTypeSchema = true),
    @ApiResponse(responseCode = "400"),
    @ApiResponse(responseCode = "401"),
    @ApiResponse(responseCode = "403"),
    @ApiResponse(responseCode = "404")
  })
  @PatchMapping("/{hotelId}/presentation")
  @PreAuthorize("hasRole('ADMIN')")
  public HotelResponse update(
      @Parameter(description = "Идентификатор отеля.") @PathVariable Long hotelId,
      @Valid @RequestBody HotelPresentationRequest request) {
    return service.update(hotelId, request);
  }
}
