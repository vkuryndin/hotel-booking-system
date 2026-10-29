package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.LoginRequest;
import com.hotelbooking.booking.dto.response.LoginResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginServiceTest {
    private AppUserRepository users;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private LoginService loginService;
    private AppUser user;

    @BeforeEach
    void setup() {
        users = mock(AppUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        loginService = new LoginService(users, passwordEncoder, jwtService);
        user = new AppUser("test@example.com", "stored-hash", Role.USER);
    }

    @Test
    void validCredentialsReturnBearerTokenAndLifetime() {
        when(users.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("signed.jwt.token");
        when(jwtService.getExpiresInSeconds()).thenReturn(3600L);

        LoginResponse response = loginService.login(new LoginRequest(" Test@Example.com ", "correct-password"));
        assertEquals("signed.jwt.token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresIn());
        assertFalse(response.passwordChangeRequired());
        verify(users).findByEmail("test@example.com");
        verify(jwtService).generateAccessToken(user);
    }

    @Test
    void missingUserRejectsWithoutGeneratingJwt() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class,
                () -> loginService.login(new LoginRequest("missing@example.com", "password")));
        verifyNoInteractions(jwtService);
    }

    @Test
    void wrongPasswordRejectsWithoutGeneratingJwt() {
        when(users.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);
        assertThrows(BadCredentialsException.class,
                () -> loginService.login(new LoginRequest("test@example.com", "wrong")));
        verifyNoInteractions(jwtService);
    }

    @Test
    void absentAndWrongCredentialsExposeSameErrorText() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        BadCredentialsException missing = assertThrows(BadCredentialsException.class,
                () -> loginService.login(new LoginRequest("missing@example.com", "password")));
        BadCredentialsException wrong = assertThrows(BadCredentialsException.class,
                () -> loginService.login(new LoginRequest("test@example.com", "wrong")));
        assertEquals(missing.getMessage(), wrong.getMessage());
    }

    @Test
    void forcedPasswordChangeFlagIsVisibleInLoginResponse() {
        user.requirePasswordChange();
        when(users.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("temp", "stored-hash")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("temp.jwt");
        LoginResponse response = loginService.login(new LoginRequest("test@example.com", "temp"));
        assertTrue(response.passwordChangeRequired());
    }
}
