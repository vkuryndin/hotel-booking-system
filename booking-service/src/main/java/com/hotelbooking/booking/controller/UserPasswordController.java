package com.hotelbooking.booking.controller;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.booking.dto.request.ChangePasswordRequest;
import com.hotelbooking.booking.service.UserPasswordService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;
import java.util.Map;

@Tag(name = "Профиль и пароль", description = "Управление собственным паролем и проверка JWT.")
@RestController
@RequestMapping("/api/users")
public class UserPasswordController {

    private final UserPasswordService passwordService;

    public UserPasswordController(
            UserPasswordService passwordService
    ) {
        this.passwordService = passwordService;
    }

    @Operation(summary = "Изменить свой пароль", description = "Проверяет текущий пароль и устанавливает новый. После смены пароля необходимо выполнить вход заново; ранее выданные пользовательские токены могут быть отозваны.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Пароль изменён; требуется повторный вход.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Превышен лимит запросов; повторите после Retry-After.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class)))
    })
    @PostMapping("/me/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        passwordService.changePassword(userId, request);

        return ResponseEntity.ok(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 200,
                "code", "PASSWORD_CHANGED",
                "message", "Password changed successfully. Please log in again.",
                "path", "/api/users/me/change-password"
        ));
    }

    @Operation(summary = "Проверить актуальность JWT", description = "Проверяет возможность использования текущего токена, включая отзыв после изменений учётной записи. Успешный ответ не содержит тела.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Токен действителен.", useReturnTypeSchema = false),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class)))
    })
    @GetMapping("/token-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> tokenStatus() {
        return ResponseEntity.noContent().build();
    }
}