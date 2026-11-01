package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.response.HotelPhotoResponse;
import com.hotelbooking.hotel.service.HotelPhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Фотографии отелей", description = "Управление галереей изображений отеля.")
@RestController
@RequestMapping("/api/hotels/{hotelId}/photos")
public class HotelPhotoController {

  private final HotelPhotoService service;

  public HotelPhotoController(HotelPhotoService service) {
    this.service = service;
  }

  @Operation(
      summary = "Получить фотографии отеля",
      description =
          "Возвращает метаданные фотографий указанного отеля с порядком и "
              + "признаком главного изображения.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Список фотографий отеля.",
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
  @GetMapping
  public List<HotelPhotoResponse> list(
      @Parameter(description = "Идентификатор отеля.") @PathVariable Long hotelId) {
    return service.list(hotelId);
  }

  @Operation(
      summary = "Скачать фотографию отеля",
      description =
          "Возвращает исходный файл фотографии; Content-Type "
              + "соответствует сохранённому типу изображения.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Бинарный файл JPEG, PNG или WebP.",
        content =
            @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary"))),
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
  @GetMapping("/{photoId}/file")
  public ResponseEntity<Resource> getFile(
      @Parameter(description = "Идентификатор отеля.") @PathVariable Long hotelId,
      @Parameter(description = "Идентификатор фотографии.") @PathVariable Long photoId) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(service.contentType(hotelId, photoId)))
        .body(service.getFile(hotelId, photoId));
  }

  @Operation(
      summary = "Загрузить фотографию отеля (ADMIN)",
      description =
          "Загружает JPEG, PNG или WebP через multipart/form-data, поле "
              + "file. Файл — от 1 байта до 5 МБ; допускается не более 10 "
              + "фотографий на отель.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Метаданные сохранённой фотографии.",
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
        responseCode = "413",
        description = "Загружаемое изображение превышает разрешённый размер.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "415",
        description = "Неподдерживаемый формат содержимого.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation = com.hotelbooking.hotel.dto.response.ErrorResponse.class)))
  })
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<HotelPhotoResponse> upload(
      @Parameter(description = "Идентификатор отеля.") @PathVariable Long hotelId,
      @Parameter(description = "Файл изображения (multipart/form-data).") @RequestPart("file")
          MultipartFile file) {
    HotelPhotoResponse photo = service.upload(hotelId, file);

    return ResponseEntity.status(HttpStatus.CREATED).body(photo);
  }

  @Operation(
      summary = "Сделать фото главным (ADMIN)",
      description = "Устанавливает выбранную фотографию главной для отеля.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Обновлённые метаданные фотографии.",
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
  @PatchMapping("/{photoId}/primary")
  @PreAuthorize("hasRole('ADMIN')")
  public HotelPhotoResponse setPrimary(
      @Parameter(description = "Идентификатор отеля.") @PathVariable Long hotelId,
      @Parameter(description = "Идентификатор фотографии.") @PathVariable Long photoId) {
    return service.setPrimary(hotelId, photoId);
  }

  @Operation(
      summary = "Изменить порядок фото отеля (ADMIN)",
      description = "Меняет позицию фотографии в галерее с помощью параметра sortOrder.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Обновлённый порядок фотографии.",
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
  @PatchMapping("/{photoId}/order")
  @PreAuthorize("hasRole('ADMIN')")
  public HotelPhotoResponse setOrder(
      @Parameter(description = "Идентификатор отеля.") @PathVariable Long hotelId,
      @Parameter(description = "Идентификатор фотографии.") @PathVariable Long photoId,
      @Parameter(description = "Порядковый номер фотографии в галерее.") @RequestParam
          int sortOrder) {
    return service.setOrder(hotelId, photoId, sortOrder);
  }

  @Operation(
      summary = "Удалить фотографию отеля (ADMIN)",
      description = "Удаляет метаданные и файл фотографии. Успешный ответ не содержит тела.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "204",
        description = "Фотография удалена.",
        useReturnTypeSchema = false),
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
  @DeleteMapping("/{photoId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> delete(
      @Parameter(description = "Идентификатор отеля.") @PathVariable Long hotelId,
      @Parameter(description = "Идентификатор фотографии.") @PathVariable Long photoId) {
    service.delete(hotelId, photoId);

    return ResponseEntity.noContent().build();
  }
}
