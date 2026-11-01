package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.request.RoomDetailsRequest;
import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.service.RoomDetailsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @ApiResponse(
        responseCode = "400",
        description = "Некорректные данные, параметры или формат запроса.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Отсутствует, истёк или недействителен JWT.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Недостаточно прав или запрещён доступ к чужим данным.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Запрошенный отель, номер или фотография не найдены.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
  })
  @PatchMapping("/{roomId}/details")
  @PreAuthorize("hasRole('ADMIN')")
  public RoomResponse update(
      @Parameter(description = "Идентификатор номера.") @PathVariable Long roomId,
      @Valid @RequestBody RoomDetailsRequest request) {
    return service.update(roomId, request);
  }
}
