package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.request.LoginRequest;
import com.hotelbooking.booking.dto.response.LoginResponse;
import com.hotelbooking.booking.service.LoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@SecurityRequirements // Public endpoint: no JWT required.
@Tag(name = "Аутентификация", description = "Получение JWT по email и паролю.")
@RestController
@RequestMapping("/api/users")
public class LoginController {

  private final LoginService loginService;

  public LoginController(LoginService loginService) {
    this.loginService = loginService;
  }

  @Operation(
      summary = "Войти и получить JWT",
      description =
          "Проверяет email и пароль, выдаёт JWT для последующих запросов. Авторизация не требуется.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "JWT и данные входа.",
        useReturnTypeSchema = true),
    @ApiResponse(
        responseCode = "400",
        description = "Некорректные данные, параметры или формат запроса.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            com.hotelbooking.booking.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Неверный email или пароль (INVALID_CREDENTIALS).",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            com.hotelbooking.booking.dto.response.ErrorResponse.class))),
    @ApiResponse(
        responseCode = "429",
        description = "Превышен лимит запросов; повторите после Retry-After.",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            com.hotelbooking.booking.dto.response.ErrorResponse.class)))
  })
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {

    LoginResponse response = loginService.login(request);

    return ResponseEntity.ok(response);
  }
}
