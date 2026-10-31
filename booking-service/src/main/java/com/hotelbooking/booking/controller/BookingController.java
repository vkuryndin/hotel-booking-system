package com.hotelbooking.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.dto.response.BookingResponse;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import java.util.UUID;

import java.net.URI;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;

@Tag(name = "Бронирования", description = "Создание, получение и отмена бронирований.")
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(summary = "Создать бронирование", description = "Создаёт бронирование для текущего пользователя. При autoSelect=false укажите roomId; при autoSelect=true — hotelId и при необходимости strategy. Даты задаются в формате YYYY-MM-DD, дата выезда позже даты заезда. Заголовок Idempotency-Key (UUID) обязателен: повтор с тем же ключом и данными не создаёт второй заказ. Подтверждение номера выполняется через Saga с Hotel Service.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Бронирование создано; статус CONFIRMED, адрес в Location.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный пользователь, бронирование, отель или номер не найден.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Номер уже занят (ROOM_ALREADY_BOOKED), нет свободных номеров, конфликт Idempotency-Key или параллельный запрос.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Hotel Service недоступен или операция Saga временно не может завершиться.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody BookingRequest request,
            @Parameter(description = "Уникальный UUID попытки бронирования. При повторах передавайте тот же ключ с теми же данными.") @RequestHeader("Idempotency-Key") UUID idempotencyKey
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        BookingResponse response =
                bookingService.createBooking(userId, request, idempotencyKey);

        return ResponseEntity
                .created(URI.create("/api/bookings/" + response.id()))
                .body(response);
    }

    @Operation(summary = "Получить свои бронирования", description = "Возвращает бронирования текущего пользователя с пагинацией: page (с 0), size, sort.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Страница собственных бронирований.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class)))
    })
    @GetMapping("/my")
    public Page<BookingResponse> getMyBookings(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return bookingService.getMyBookings(userId, pageable);
    }
    @Operation(summary = "Получить бронирование по ID", description = "Получает бронирование по идентификатору. USER может получить только своё бронирование; ADMIN — любое.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Данные бронирования.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный пользователь, бронирование, отель или номер не найден.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public BookingResponse getBookingById(
            @Parameter(description = "Идентификатор бронирования.") @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @CurrentSecurityContext(expression = "authentication")
            Authentication authentication
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return bookingService.getBookingById(
                id, userId, isAdmin(authentication)
        );
    }
    @Operation(summary = "Отменить бронирование", description = "Отменяет существующее бронирование. USER может отменять свои записи; ADMIN — любые. Освобождение номера выполняется через Hotel Service; повторная отмена/неподходящий статус могут вернуть конфликт.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Бронирование отменено.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный пользователь, бронирование, отель или номер не найден.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Конфликт состояния, данных или уникальности; операция не выполнена.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Hotel Service недоступен или операция Saga временно не может завершиться.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class)))
    })
    @PatchMapping("/{id}/cancel")
    public BookingResponse cancelBooking(
            @Parameter(description = "Идентификатор бронирования.") @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @CurrentSecurityContext(expression = "authentication")
            Authentication authentication
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return bookingService.cancelBooking(
                id, userId, isAdmin(authentication)
        );
    }

    @Operation(summary = "Получить все бронирования (ADMIN)", description = "Административный постраничный список бронирований с необязательным фильтром по статусу. Пагинация: page (с 0), size, sort.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Страница бронирований.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.booking.dto.response.ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<BookingResponse> getAllBookings(
            @Parameter(description = "Необязательный фильтр по статусу бронирования.") @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return bookingService.getAllBookings(status, pageable);
    }
    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority())
                );
    }
}