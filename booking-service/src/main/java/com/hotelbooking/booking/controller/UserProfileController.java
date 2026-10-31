package com.hotelbooking.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.booking.dto.request.UpdateMyProfileRequest;
import com.hotelbooking.booking.dto.response.UserResponse;
import com.hotelbooking.booking.service.UserProfileService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Профиль и пароль", description = "Просмотр и изменение данных текущего пользователя.")
@RestController
@RequestMapping("/api/users/me")
public class UserProfileController {

    private final UserProfileService profileService;

    public UserProfileController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "Получить свой профиль", description = "Возвращает профиль пользователя, которому принадлежит переданный JWT.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Данные текущего пользователя.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public UserResponse getMyProfile(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return profileService.getMyProfile(
                Long.valueOf(jwt.getSubject())
        );
    }

    @Operation(summary = "Изменить свой профиль", description = "Частично изменяет доступные владельцу поля профиля: имя пользователя, телефон, имя и фамилию.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Обновлённые данные профиля.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Конфликт состояния, данных или уникальности; операция не выполнена.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class)))
    })
    @PatchMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public UserResponse updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        return profileService.updateMyProfile(
                Long.valueOf(jwt.getSubject()),
                request
        );
    }
}