package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.dto.request.CreateAdminUserRequest;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.repository.AppUserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.hotelbooking.booking.repository.BookingRepository;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.hotelbooking.booking.exception.UsernameAlreadyRegisteredException;
@Service
public class AdminUserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BookingRepository bookingRepository;

    public AdminUserService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            BookingRepository bookingRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public AdminUserResponse createUser(CreateAdminUserRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);
        String username = request.username()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyRegisteredException();
        }

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User already exists"
            );
        }

        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password exceeds 72 UTF-8 bytes"
            );
        }

        AppUser user = new AppUser(
                email,
                passwordEncoder.encode(request.password()),
                request.role(),
                username,
                request.phone() == null || request.phone().isEmpty()
                        ? null : request.phone(),
                request.firstName().trim(),
                request.lastName().trim()
        );

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User already exists",
                    ex
            );
        }

        return AdminUserResponse.from(user);
    }
    @Transactional
    public void deleteUser(Long id) {

        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found: " + id
                ));

        if (bookingRepository.existsByUserId(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete user with booking history"
            );
        }

        userRepository.delete(user);
        userRepository.flush();
    }
}