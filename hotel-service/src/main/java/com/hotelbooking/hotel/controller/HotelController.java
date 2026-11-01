package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.request.HotelRequest;
import com.hotelbooking.hotel.dto.response.HotelResponse;
import com.hotelbooking.hotel.entity.HotelAmenity;
import com.hotelbooking.hotel.service.HotelService;
import com.hotelbooking.hotel.strategy.RoomSelectionMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Отели", description = "Поиск и управление отелями и стратегиями подбора номеров.")
@RestController
@RequestMapping("/api/hotels")
public class HotelController {

  private final HotelService hotelService;

  public HotelController(HotelService hotelService) {
    this.hotelService = hotelService;
  }

  @Operation(
      summary = "Создать отель (ADMIN)",
      description =
          "Создаёт отель по названию и адресу. Описание, рейтинг, "
              + "удобства и изображения настраиваются отдельными операциями.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Отель создан; Location указывает его адрес.",
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
        responseCode = "409",
        description = "Отель с такими данными уже существует либо ресурс используется.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
  })
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<HotelResponse> createHotel(@Valid @RequestBody HotelRequest request) {

    HotelResponse response = hotelService.createHotel(request);

    URI location = URI.create("/api/hotels/" + response.id());

    return ResponseEntity.created(location).body(response);
  }

  @Operation(
      summary = "Поиск отелей",
      description =
          "Возвращает постраничный список отелей с фильтрами по названию, "
              + "адресу, минимальному рейтингу и удобству. sortBy и direction "
              + "задают сортировку; page (с 0), size — пагинацию. Требуется JWT "
              + "USER/ADMIN.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Страница отелей.",
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
                        implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
  })
  @GetMapping
  public ResponseEntity<Page<HotelResponse>> getHotels(
      @Parameter(description = "Фильтр по названию отеля.") @RequestParam(required = false)
          String name,
      @Parameter(description = "Фильтр по адресу отеля.") @RequestParam(required = false)
          String address,
      @Parameter(description = "Минимальная оценка отеля.") @RequestParam(required = false)
          BigDecimal minRating,
      @Parameter(description = "Фильтр по одному удобству.") @RequestParam(required = false)
          HotelAmenity amenity,
      @Parameter(description = "Поле сортировки (по умолчанию id).")
          @RequestParam(defaultValue = "id")
          String sortBy,
      @Parameter(description = "Направление сортировки: asc или desc.")
          @RequestParam(defaultValue = "asc")
          String direction,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        hotelService.searchHotels(name, address, minRating, amenity, sortBy, direction, pageable));
  }

  @Operation(
      summary = "Получить отель по ID",
      description = "Возвращает сведения об отеле по его идентификатору.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Данные отеля.", useReturnTypeSchema = true),
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
  @GetMapping("/{id}")
  public ResponseEntity<HotelResponse> getHotelById(
      @Parameter(description = "Идентификатор отеля.") @PathVariable Long id) {
    return ResponseEntity.ok(hotelService.getHotelById(id));
  }

  @Operation(
      summary = "Изменить отель (ADMIN)",
      description = "Обновляет название и адрес существующего отеля.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Обновлённые данные отеля.",
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
                        implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Отель с такими данными уже существует либо ресурс используется.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
  })
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<HotelResponse> updateHotel(
      @Parameter(description = "Идентификатор отеля.") @PathVariable Long id,
      @Valid @RequestBody HotelRequest request) {
    return ResponseEntity.ok(hotelService.updateHotel(id, request));
  }

  @Operation(
      summary = "Получить стратегию подбора номеров",
      description = "Возвращает действующую для отеля стратегию автоматического выбора номера.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Стратегия выбора номера.",
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
  @GetMapping("/{id}/room-selection-strategy")
  public ResponseEntity<RoomSelectionMode> getRoomSelectionStrategy(
      @Parameter(description = "Идентификатор отеля.") @PathVariable Long id) {
    return ResponseEntity.ok(hotelService.getRoomSelectionStrategy(id));
  }

  @Operation(
      summary = "Изменить стратегию подбора (ADMIN)",
      description =
          "Устанавливает стратегию выбора номера на уровне отеля: "
              + "LEAST_BOOKED (реже бронировали) или MOST_EXPENSIVE (дороже).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Новая стратегия выбора.",
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
  @PutMapping("/{id}/room-selection-strategy")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<RoomSelectionMode> updateRoomSelectionStrategy(
      @Parameter(description = "Идентификатор отеля.") @PathVariable Long id,
      @Valid @RequestBody com.hotelbooking.hotel.dto.request.RoomSelectionStrategyRequest request) {
    return ResponseEntity.ok(hotelService.updateRoomSelectionStrategy(id, request.strategy()));
  }

  @Operation(
      summary = "Удалить отель (ADMIN)",
      description =
          "Удаляет отель, если нет ограничений целостности, запрещающих "
              + "удаление используемых записей. Успешный ответ без тела.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Отель удалён.", useReturnTypeSchema = false),
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
                        implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Отель с такими данными уже существует либо ресурс используется.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
  })
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> deleteHotel(
      @Parameter(description = "Идентификатор отеля.") @PathVariable Long id) {
    hotelService.deleteHotel(id);
    return ResponseEntity.noContent().build();
  }
}
