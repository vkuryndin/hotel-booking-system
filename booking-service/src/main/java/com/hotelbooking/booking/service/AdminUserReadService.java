package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.repository.AppUserRepository;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hotelbooking.booking.exception.UserOperationException;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class AdminUserReadService {

    private final AppUserRepository userRepository;

    public AdminUserReadService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getAllUsers(
            Pageable pageable
    ) {
        if (pageable.getPageSize() > 100) {
            throw new IllegalArgumentException(
                    "Page size must not exceed 100"
            );
        }

        Pageable pageRequest = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("id")
        );

        return userRepository.findAll(pageRequest)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new UserOperationException(
                        UserOperationException.Reason.USER_NOT_FOUND,
                        "User not found: " + id
                ));

        return toResponse(user);
    }

    private AdminUserResponse toResponse(AppUser user) {
        return AdminUserResponse.from(user);

    }
}