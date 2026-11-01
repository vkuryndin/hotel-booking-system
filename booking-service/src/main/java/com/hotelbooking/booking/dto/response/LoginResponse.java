package com.hotelbooking.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
    @Schema(description = "JWT для заголовка Authorization: Bearer <токен>.") String accessToken,
    @Schema(description = "Тип выданного токена (Bearer).") String tokenType,
    @Schema(description = "Срок действия токена в секундах.") long expiresIn,
    @Schema(description = "Требуется сменить пароль.") boolean passwordChangeRequired,
    @Schema(description = "Имя учётной записи.") String username,
    @Schema(description = "Имя пользователя.") String firstName,
    @Schema(description = "Фамилия пользователя.") String lastName,
    @Schema(description = "Email пользователя.") String email) {

  // Preserve existing Java callers using the original four-field constructor.
  public LoginResponse(
      String accessToken, String tokenType, long expiresIn, boolean passwordChangeRequired) {
    this(accessToken, tokenType, expiresIn, passwordChangeRequired, null, null, null, null);
  }
}
