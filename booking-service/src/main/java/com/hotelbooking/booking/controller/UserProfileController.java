package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.request.UpdateMyProfileRequest;
import com.hotelbooking.booking.dto.response.UserResponse;
import com.hotelbooking.booking.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Профиль и пароль", description = "Просмотр и изменение данных текущего пользователя.")
@RestController
@RequestMapping("/api/users/me")
public class UserProfileController {

  private final UserProfileService profileService;

  public UserProfileController(UserProfileService profileService) {
    this.profileService = profileService;
  }

  @Operation(
      summary = "Получить свой профиль",
      description = "Возвращает профиль пользователя, которому принадлежит переданный JWT.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Данные текущего пользователя.",
        useReturnTypeSchema = true),
    @ApiResponse(responseCode = "401"),
    @ApiResponse(responseCode = "403")
  })
  @GetMapping
  @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
  public UserResponse getMyProfile(@AuthenticationPrincipal Jwt jwt) {
    return profileService.getMyProfile(Long.valueOf(jwt.getSubject()));
  }

  @Operation(
      summary = "Изменить свой профиль",
      description =
          "Частично изменяет доступные владельцу поля профиля: имя "
              + "пользователя, телефон, имя и фамилию.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Обновлённые данные профиля.",
        useReturnTypeSchema = true),
    @ApiResponse(responseCode = "400"),
    @ApiResponse(responseCode = "401"),
    @ApiResponse(responseCode = "403"),
    @ApiResponse(responseCode = "409")
  })
  @PatchMapping
  @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
  public UserResponse updateMyProfile(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateMyProfileRequest request) {
    return profileService.updateMyProfile(Long.valueOf(jwt.getSubject()), request);
  }
}
