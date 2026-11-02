package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.request.RoomDetailsRequest;
import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.service.RoomDetailsService;
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

@Tag(name = "Характеристики номеров", description = "Изменение характеристик номера.")
@RestController
@RequestMapping("/api/rooms")
public class RoomDetailsController {

  private final RoomDetailsService service;

  public RoomDetailsController(RoomDetailsService service) {
    this.service = service;
  }

  @Operation(
      summary = "Обновить характеристики номера (ADMIN)",
      description = "Частично изменяет категорию, вместимость, цену за ночь и удобства номера.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Обновлённые данные номера.",
        useReturnTypeSchema = true),
    @ApiResponse(responseCode = "400"),
    @ApiResponse(responseCode = "401"),
    @ApiResponse(responseCode = "403"),
    @ApiResponse(responseCode = "404")
  })
  @PatchMapping("/{roomId}/details")
  @PreAuthorize("hasRole('ADMIN')")
  public RoomResponse update(
      @Parameter(description = "Идентификатор номера.") @PathVariable Long roomId,
      @Valid @RequestBody RoomDetailsRequest request) {
    return service.update(roomId, request);
  }
}
