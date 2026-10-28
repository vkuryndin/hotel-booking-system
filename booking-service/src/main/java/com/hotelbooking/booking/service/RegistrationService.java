package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.RegisterRequest;
import com.hotelbooking.booking.dto.response.UserResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.exception.EmailAlreadyRegisteredException;
import com.hotelbooking.booking.mapper.AppUserMapper;
import com.hotelbooking.booking.repository.AppUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.hotelbooking.booking.exception.UsernameAlreadyRegisteredException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class RegistrationService {

    private static final Logger log =
            LoggerFactory.getLogger(RegistrationService.class);

    private final AppUserRepository userRepository;
    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            AppUserRepository userRepository,
            AppUserMapper userMapper,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);
        String username = request.username()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyRegisteredException();
        }

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Password must not exceed 72 UTF-8 bytes"
            );
        }

        String passwordHash = passwordEncoder.encode(request.password());

        AppUser user = new AppUser(
                email,
                passwordHash,
                Role.USER,
                username,
                request.phone() == null || request.phone().isEmpty()
                        ? null : request.phone(),
                request.firstName().trim(),
                request.lastName().trim()
        );

        AppUser savedUser;

        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email or username already exists",
                    ex
            );
        }

        log.info("User registered: id={}", savedUser.getId());

        return userMapper.toResponse(savedUser);


    }
}