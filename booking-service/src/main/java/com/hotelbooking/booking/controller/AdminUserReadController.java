package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.service.AdminUserReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Администрирование пользователей",
    description = "Административный просмотр и поиск пользователей.")
@RestController
@RequestMapping("/api/users")
public class AdminUserReadController {

  private final AdminUserReadService readService;

  public AdminUserReadController(AdminUserReadService readService) {
    this.readService = readService;
  }

  @Operation(
      summary = "Получить пользователей (ADMIN)",
      description =
          "Возвращает постраничный список пользователей. Необязательный q ищет по "
              + "username, имени, фамилии и email без учёта регистра. "
              + "Пагинация: page (с 0), size, sort.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Страница пользователей.",
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
                            com.hotelbooking.booking.dto.response.ErrorResponse.class)))
  })
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public Page<AdminUserResponse> getAllUsers(
      @PageableDefault(size = 20) Pageable pageable,
      @Parameter(description = "Необязательный поиск по username, имени, фамилии и email.")
          @RequestParam(name = "q", required = false)
          String query) {
    return readService.getAllUsers(pageable, query);
  }

  @Operation(
      summary = "Получить пользователя по ID (ADMIN)",
      description = "Возвращает сведения об указанном пользователе.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Данные пользователя.",
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
                            com.hotelbooking.booking.dto.response.ErrorResponse.class)))
  })
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public AdminUserResponse getUserById(
      @Parameter(description = "Идентификатор пользователя.") @PathVariable Long id) {
    return readService.getUserById(id);
  }
}
