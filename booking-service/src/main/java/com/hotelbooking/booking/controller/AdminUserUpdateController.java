package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.request.ResetAdminPasswordRequest;
import com.hotelbooking.booking.dto.request.UpdateAdminUserRequest;
import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.dto.response.ResetAdminPasswordResponse;
import com.hotelbooking.booking.service.AdminUserUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Администрирование пользователей",
    description = "Редактирование учётных записей и сброс паролей администратором.")
@RestController
@RequestMapping("/api/users")
public class AdminUserUpdateController {

  private final AdminUserUpdateService updateService;

  public AdminUserUpdateController(AdminUserUpdateService updateService) {
    this.updateService = updateService;
  }

  @Operation(
      summary = "Изменить пользователя (ADMIN)",
      description =
          "Частично обновляет учётную запись. Передаются только "
              + "изменяемые поля; действуют ограничения на изменение "
              + "собственной роли и последнего администратора.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Обновлённые данные пользователя.",
        useReturnTypeSchema = true),
    @ApiResponse(responseCode = "400"),
    @ApiResponse(responseCode = "401"),
    @ApiResponse(responseCode = "403"),
    @ApiResponse(responseCode = "404"),
    @ApiResponse(
        responseCode = "409",
        description = "Конфликт учётной записи или запрет изменения последнего администратора."),
    @ApiResponse(responseCode = "429")
  })
  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public AdminUserResponse updateUser(
      @Parameter(description = "Идентификатор пользователя для изменения или сброса пароля.")
          @PathVariable
          Long id,
      @Valid @RequestBody UpdateAdminUserRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    Long actingAdminId = Long.valueOf(jwt.getSubject());

    return updateService.updateUser(id, request, actingAdminId);
  }

  @Operation(
      summary = "Сбросить пароль пользователя (ADMIN)",
      description = "Администратор задаёт новый пароль выбранному пользователю.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Пароль пользователя изменён.",
        useReturnTypeSchema = true),
    @ApiResponse(responseCode = "400"),
    @ApiResponse(responseCode = "401"),
    @ApiResponse(responseCode = "403"),
    @ApiResponse(responseCode = "404"),
    @ApiResponse(responseCode = "429")
  })
  @PostMapping("/{id}/reset-password")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ResetAdminPasswordResponse> resetPassword(
      @Parameter(description = "Идентификатор пользователя для изменения или сброса пароля.")
          @PathVariable
          Long id,
      @Valid @RequestBody ResetAdminPasswordRequest request) {
    updateService.resetPassword(id, request);

    return ResponseEntity.ok(
        new ResetAdminPasswordResponse(
            Instant.now(),
            200,
            "PASSWORD_RESET",
            "Password reset successfully",
            "/api/users/" + id + "/reset-password"));
  }
}
