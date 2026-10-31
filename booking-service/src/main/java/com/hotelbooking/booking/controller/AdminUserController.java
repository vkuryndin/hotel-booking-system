package com.hotelbooking.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.dto.request.CreateAdminUserRequest;
import com.hotelbooking.booking.service.AdminUserService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.DeleteMapping;
import java.time.Instant;
import java.util.Map;

import java.net.URI;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
@Tag(name = "Администрирование пользователей", description = "Создание и удаление учётных записей администратором.")
@RestController
@RequestMapping("/api/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "Создать пользователя (ADMIN)", description = "Администратор создаёт учётную запись с указанной ролью и персональными данными.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Пользователь создан; заголовок Location содержит его адрес.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Конфликт уникального email/username или ограничение на удаление администратора.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserResponse> createUser(
            @Valid @RequestBody CreateAdminUserRequest request
    ) {
        AdminUserResponse response = adminUserService.createUser(request);

        return ResponseEntity
                .created(URI.create("/api/users/" + response.id()))
                .body(response);
    }
    @Operation(summary = "Удалить пользователя (ADMIN)", description = "Администратор удаляет учётную запись. Нельзя удалить собственную запись или последнего активного администратора.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Учётная запись удалена.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный пользователь, бронирование, отель или номер не найден.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Конфликт уникального email/username или ограничение на удаление администратора.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @Parameter(description = "Идентификатор пользователя для удаления.") @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long actingAdminId = Long.valueOf(jwt.getSubject());

        adminUserService.deleteUser(id, actingAdminId);

        return ResponseEntity.ok(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 200,
                "code", "USER_DELETED",
                "message", "User deleted successfully",
                "path", "/api/users/" + id
        ));
    }

}