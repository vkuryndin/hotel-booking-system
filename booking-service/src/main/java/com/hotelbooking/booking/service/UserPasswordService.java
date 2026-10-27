package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.ChangePasswordRequest;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.repository.AppUserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
public class UserPasswordService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserPasswordService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(
            Long userId,
            ChangePasswordRequest request
    ) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPasswordHash()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Current password is incorrect"
            );
        }

        if (passwordEncoder.matches(
                request.newPassword(),
                user.getPasswordHash()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password must differ from current password"
            );
        }

        if (request.newPassword()
                .getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password exceeds 72 UTF-8 bytes"
            );
        }

        user.completePasswordChange(
                passwordEncoder.encode(request.newPassword())
        );

        userRepository.saveAndFlush(user);
    }
}