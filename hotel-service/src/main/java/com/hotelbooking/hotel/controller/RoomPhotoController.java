package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.response.RoomPhotoResponse;
import com.hotelbooking.hotel.service.RoomPhotoService;
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

@Tag(name = "Фотографии номеров", description = "Управление галереей изображений номера.")
@RestController
@RequestMapping("/api/rooms/{roomId}/photos")
public class RoomPhotoController {

  private final RoomPhotoService service;

  public RoomPhotoController(RoomPhotoService service) {
    this.service = service;
  }

  @Operation(
      summary = "Получить фотографии номера",
      description = "Возвращает метаданные фотографий комнаты и их порядок.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Список фотографий номера.",
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
  public List<RoomPhotoResponse> list(
      @Parameter(description = "Идентификатор номера.") @PathVariable Long roomId) {
    return service.list(roomId);
  }

  @Operation(
      summary = "Скачать фотографию номера",
      description = "Возвращает файл выбранной фотографии номера с корректным Content-Type.")
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
      @Parameter(description = "Идентификатор номера.") @PathVariable Long roomId,
      @Parameter(description = "Идентификатор фотографии.") @PathVariable Long photoId) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(service.contentType(roomId, photoId)))
        .body(service.getFile(roomId, photoId));
  }

  @Operation(
      summary = "Загрузить фотографию номера (ADMIN)",
      description =
          "Загружает JPEG, PNG или WebP через multipart/form-data, поле "
              + "file. Файл — от 1 байта до 5 МБ; допускается не более 10 "
              + "фотографий на номер.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Метаданные фотографии номера.",
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
  public ResponseEntity<RoomPhotoResponse> upload(
      @Parameter(description = "Идентификатор номера.") @PathVariable Long roomId,
      @Parameter(description = "Файл изображения (multipart/form-data).") @RequestPart("file")
          MultipartFile file) {
    RoomPhotoResponse photo = service.upload(roomId, file);

    return ResponseEntity.status(HttpStatus.CREATED).body(photo);
  }

  @Operation(
      summary = "Сделать фото номера главным (ADMIN)",
      description = "Отмечает фотографию главной для выбранного номера.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Метаданные главной фотографии.",
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
  public RoomPhotoResponse setPrimary(
      @Parameter(description = "Идентификатор номера.") @PathVariable Long roomId,
      @Parameter(description = "Идентификатор фотографии.") @PathVariable Long photoId) {
    return service.setPrimary(roomId, photoId);
  }

  @Operation(
      summary = "Изменить порядок фото номера (ADMIN)",
      description = "Изменяет порядок изображения в галерее комнаты с помощью sortOrder.")
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
  @PatchMapping("/{photoId}/order")
  @PreAuthorize("hasRole('ADMIN')")
  public RoomPhotoResponse setOrder(
      @Parameter(description = "Идентификатор номера.") @PathVariable Long roomId,
      @Parameter(description = "Идентификатор фотографии.") @PathVariable Long photoId,
      @Parameter(description = "Порядковый номер фотографии в галерее.") @RequestParam
          int sortOrder) {
    return service.setOrder(roomId, photoId, sortOrder);
  }

  @Operation(
      summary = "Удалить фотографию номера (ADMIN)",
      description = "Удаляет изображение и его метаданные. Успешный ответ без тела.")
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
      @Parameter(description = "Идентификатор номера.") @PathVariable Long roomId,
      @Parameter(description = "Идентификатор фотографии.") @PathVariable Long photoId) {
    service.delete(roomId, photoId);

    return ResponseEntity.noContent().build();
  }
}
