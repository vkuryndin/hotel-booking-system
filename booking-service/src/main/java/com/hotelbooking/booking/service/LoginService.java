package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.LoginRequest;
import com.hotelbooking.booking.dto.response.LoginResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.repository.AppUserRepository;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class LoginService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "Invalid email or password"
                        )
                );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash())) {

            throw new BadCredentialsException(
                    "Invalid email or password"
            );
        }

        String token = jwtService.generateAccessToken(user);

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpiresInSeconds(),
                user.isPasswordChangeRequired()
        );
    }
}