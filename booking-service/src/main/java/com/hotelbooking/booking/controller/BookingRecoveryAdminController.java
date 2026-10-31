package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.response.ExhaustedRecoveryResponse;
import com.hotelbooking.booking.service.ManualBookingRecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(
    name = "Восстановление Saga (ADMIN)",
    description = "Ручное восстановление Saga после исчерпания автоматических попыток.")
@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('ADMIN')")
public class BookingRecoveryAdminController {
  private final ManualBookingRecoveryService manualRecovery;

  public BookingRecoveryAdminController(ManualBookingRecoveryService manualRecovery) {
    this.manualRecovery = manualRecovery;
  }

  @Operation(
      summary = "Получить исчерпанные попытки восстановления Saga (ADMIN)",
      description =
          "Возвращает до 100 бронирований, у которых автоматическое восстановление Saga исчерпало попытки и требуется вмешательство администратора.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Список проблемных восстановлений.",
        useReturnTypeSchema = true),
    @ApiResponse(
        responseCode = "401",
        description = "Отсутствует, истёк или недействителен JWT.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            com.hotelbooking.booking.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Недостаточно прав или запрещён доступ к чужим данным.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            com.hotelbooking.booking.dto.response.ErrorResponse.class)))
  })
  @GetMapping("/recovery/exhausted")
  public List<ExhaustedRecoveryResponse> exhausted() {
    return manualRecovery.exhausted();
  }

  @Operation(
      summary = "Повторить восстановление Saga (ADMIN)",
      description =
          "Помещает ранее исчерпавшее попытки восстановление бронирования в очередь повторной обработки. HTTP 202 означает приём запроса, а не завершение восстановления.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Повторная обработка запланирована.",
        useReturnTypeSchema = true),
    @ApiResponse(
        responseCode = "400",
        description = "Некорректные данные, параметры или формат запроса.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            com.hotelbooking.booking.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Отсутствует, истёк или недействителен JWT.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            com.hotelbooking.booking.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Недостаточно прав или запрещён доступ к чужим данным.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            com.hotelbooking.booking.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Запрошенный пользователь, бронирование, отель или номер не найден.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            com.hotelbooking.booking.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Конфликт состояния, данных или уникальности; операция не выполнена.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            com.hotelbooking.booking.dto.response.ErrorResponse.class)))
  })
  @PostMapping("/{id}/recovery/retry")
  public ResponseEntity<ExhaustedRecoveryResponse> retry(
      @Parameter(description = "Идентификатор бронирования для восстановления.") @PathVariable
          Long id) {
    return ResponseEntity.accepted().body(manualRecovery.requeue(id));
  }
}
