package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.request.RegisterRequest;
import com.hotelbooking.booking.dto.response.UserResponse;
import com.hotelbooking.booking.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirements
@Tag(name = "Регистрация", description = "Регистрация новых учётных записей.")
@RestController
@RequestMapping("/api/users")
public class RegistrationController {

  private final RegistrationService registrationService;

  public RegistrationController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @Operation(
      summary = "Зарегистрировать пользователя",
      description =
          "Создаёт самостоятельную учётную запись USER по email, "
              + "паролю и персональным данным. Авторизация не требуется.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Учётная запись создана.",
        useReturnTypeSchema = true),
    @ApiResponse(responseCode = "400"),
    @ApiResponse(
        responseCode = "409",
        description = "Email или имя пользователя уже зарегистрированы."),
    @ApiResponse(responseCode = "429")
  })
  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {

    UserResponse response = registrationService.register(request);

    URI location = URI.create("/api/users/" + response.id());

    return ResponseEntity.created(location).body(response);
  }
}
