package com.hotelbooking.hotel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import com.hotelbooking.hotel.dto.request.RoomAvailabilityRequest;
import com.hotelbooking.hotel.dto.request.RoomRequest;
import com.hotelbooking.hotel.dto.request.RoomUpdateRequest;
import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.service.RoomService;
import com.hotelbooking.hotel.strategy.RoomSelectionMode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.URI;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import com.hotelbooking.hotel.entity.RoomAmenity;
import com.hotelbooking.hotel.entity.RoomCategory;
import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
@Tag(name = "Номера и подбор", description = "Поиск, подбор и административное управление номерами.")
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @Operation(
            summary = "Создать номер (ADMIN)",
            description =
                    "Добавляет номер в указанный отель. Номер комнаты должен быть "
                    + "уникален в пределах отеля."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Номер создан; Location указывает его адрес.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный отель, номер или фотография не найдены.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Номер с таким обозначением уже существует или связан с бронированиями.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody RoomRequest request
    ) {
        RoomResponse response = roomService.createRoom(request);

        return ResponseEntity
                .created(URI.create("/api/rooms/" + response.id()))
                .body(response);
    }
    @Operation(
            summary = "Поиск номеров отеля",
            description =
                    "Возвращает постраничный список номеров отеля с фильтрами по "
                    + "категории, вместимости, цене, доступности и удобствам. Для "
                    + "сортировки используйте sortBy и direction; для пагинации — "
                    + "page (с 0), size."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Страница номеров.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный отель, номер или фотография не найдены.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<Page<RoomResponse>> getRooms(
            @Parameter(description = "Идентификатор отеля.") @RequestParam Long hotelId,
            @Parameter(description = "Категория номера.") @RequestParam(required = false) RoomCategory category,
            @Parameter(description = "Минимальная вместимость номера.") @RequestParam(required = false) Integer minCapacity,
            @Parameter(description = "Минимальная цена за ночь.") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Максимальная цена за ночь.") @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Фильтр по административному признаку доступности номера.") @RequestParam(required = false) Boolean available,
            @Parameter(description = "Фильтр по одному удобству.") @RequestParam(required = false) RoomAmenity amenity,
            @Parameter(description = "Поле сортировки (по умолчанию id).") @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Направление сортировки: asc или desc.") @RequestParam(defaultValue = "asc") String direction,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
                roomService.searchRooms(
                        hotelId,
                        category,
                        minCapacity,
                        minPrice,
                        maxPrice,
                        available,
                        amenity,
                        sortBy,
                        direction,
                        pageable
                )
        );
    }
    @Operation(
            summary = "Получить номер по ID",
            description =
                    "Возвращает сведения о номере, включая его доступность, "
                    + "стоимость и счётчик бронирований."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Данные номера.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный отель, номер или фотография не найдены.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoomById(
            @Parameter(description = "Идентификатор номера.") @PathVariable Long id
    ) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }
    @Operation(
            summary = "Изменить доступность номера (ADMIN)",
            description =
                    "Включает или выключает административный признак available для "
                    + "номера. Это не замена проверке пересечения дат при "
                    + "бронировании."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Обновлённые данные номера.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный отель, номер или фотография не найдены.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Номер с таким обозначением уже существует или связан с бронированиями.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
    })
    @PatchMapping("/{id}/availability")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> updateAvailability(
            @Parameter(description = "Идентификатор номера.") @PathVariable Long id,
            @Valid @RequestBody RoomAvailabilityRequest request
    ) {
        return ResponseEntity.ok(
                roomService.updateAvailability(id, request)
        );
    }
    @Operation(
            summary = "Получить доступные номера отеля",
            description =
                    "Возвращает постраничный список номеров с флагом "
                    + "available=true. Для доступности на конкретные даты используйте "
                    + "/api/rooms/recommend."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Страница доступных номеров.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный отель, номер или фотография не найдены.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
    })
    @GetMapping("/available")
    public ResponseEntity<Page<RoomResponse>> getAvailableRooms(
            @Parameter(description = "Идентификатор отеля.") @RequestParam Long hotelId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
                roomService.getAvailableRoomsByHotelId(hotelId, pageable)
        );
    }

    @Operation(
            summary = "Изменить номер комнаты (ADMIN)",
            description =
                    "Обновляет номер (number) существующей комнаты. Для изменения "
                    + "категории и удобств используйте /api/rooms/{roomId}/details."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Обновлённые данные номера.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный отель, номер или фотография не найдены.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Номер с таким обозначением уже существует или связан с бронированиями.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> updateRoom(
            @Parameter(description = "Идентификатор номера.") @PathVariable Long id,
            @Valid @RequestBody RoomUpdateRequest request
    ) {
        return ResponseEntity.ok(roomService.updateRoom(id, request));
    }

    @Operation(summary = "Удалить номер (ADMIN)", description = "Удаляет комнату, если это не нарушает ограничения по связанным бронированиям.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Номер удалён.", useReturnTypeSchema = false),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный отель, номер или фотография не найдены.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Номер с таким обозначением уже существует или связан с бронированиями.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRoom(
            @Parameter(description = "Идентификатор номера.") @PathVariable Long id
    ) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }
    @Operation(
            summary = "Подобрать свободные номера на даты",
            description =
                    "Возвращает номера, свободные на период [checkIn, checkOut), с "
                    + "учётом числа гостей. Порядок определяется стратегией (явно "
                    + "переданной либо заданной у отеля): LEAST_BOOKED или "
                    + "MOST_EXPENSIVE. Уже занятые на период номера не попадают в "
                    + "ответ."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Отсортированный список свободных номеров.", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Некорректные данные, параметры или формат запроса.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Отсутствует, истёк или недействителен JWT.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав или запрещён доступ к чужим данным.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Запрошенный отель, номер или фотография не найдены.", content = @Content(schema = @Schema(implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
    })
    @GetMapping("/recommend")
    public ResponseEntity<List<RoomResponse>> recommendRooms(
            @Parameter(description = "Идентификатор отеля.") @RequestParam Long hotelId,

            @Parameter(description = "Дата заезда в формате YYYY-MM-DD (включительно).") @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate checkIn,

            @Parameter(description = "Дата выезда в формате YYYY-MM-DD (не включается в бронирование).") @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate checkOut,

            @Parameter(
                    description =
                            "Стратегия сортировки: LEAST_BOOKED или MOST_EXPENSIVE; если не "
                            + "указана — настройка отеля."
            ) @RequestParam(required = false)
            RoomSelectionMode strategy,

            @Parameter(description = "Количество гостей (по умолчанию 1).") @RequestParam(defaultValue = "1")
            int guestCount
    ) {
        return ResponseEntity.ok(
                roomService.recommendRooms(
                        hotelId,
                        checkIn,
                        checkOut,
                        strategy,
                        guestCount
                )
        );
    }
}