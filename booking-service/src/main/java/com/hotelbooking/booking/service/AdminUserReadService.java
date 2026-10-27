package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.repository.AppUserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AdminUserReadService {

    private final AppUserRepository userRepository;

    public AdminUserReadService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found: " + id
                ));

        return toResponse(user);
    }

    private AdminUserResponse toResponse(AppUser user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }
}