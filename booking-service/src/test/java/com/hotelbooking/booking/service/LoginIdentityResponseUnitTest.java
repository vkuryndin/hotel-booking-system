package com.hotelbooking.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hotelbooking.booking.dto.request.LoginRequest;
import com.hotelbooking.booking.dto.response.LoginResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.repository.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginIdentityResponseUnitTest {

  private final AppUserRepository users = mock(AppUserRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final JwtService jwtService = mock(JwtService.class);
  private LoginService loginService;

  @BeforeEach
  void setUp() {
    loginService = new LoginService(users, encoder, jwtService);
  }

  @Test
  void loginIncludesIdentityEvenWhenPasswordChangeIsRequired() {
    AppUser user =
        new AppUser("admin@example.com", "hash", Role.ADMIN, "admin42", null, "Ivan", "Petrov");
    user.requirePasswordChange();
    when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user));
    when(encoder.matches("temporary-password", "hash")).thenReturn(true);
    when(jwtService.generateAccessToken(user)).thenReturn("signed.jwt");
    when(jwtService.getExpiresInSeconds()).thenReturn(3600L);

    LoginResponse response =
        loginService.login(new LoginRequest(" ADMIN@EXAMPLE.COM ", "temporary-password"));

    assertEquals("signed.jwt", response.accessToken());
    assertEquals("Bearer", response.tokenType());
    assertEquals(3600L, response.expiresIn());
    assertTrue(response.passwordChangeRequired());
    assertEquals("admin42", response.username());
    assertEquals("Ivan", response.firstName());
    assertEquals("Petrov", response.lastName());
    assertEquals("admin@example.com", response.email());
  }

  @Test
  void originalFourArgumentConstructorRemainsUsable() {
    LoginResponse response = new LoginResponse("token", "Bearer", 3600L, false);
    assertEquals("token", response.accessToken());
    assertFalse(response.passwordChangeRequired());
    assertEquals(null, response.username());
  }

  @Test
  void failedLoginDoesNotGenerateJwtOrReturnIdentity() {
    when(users.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
    assertThrows(
        BadCredentialsException.class,
        () -> loginService.login(new LoginRequest("unknown@example.com", "wrong")));
    verifyNoInteractions(jwtService);
  }
}
