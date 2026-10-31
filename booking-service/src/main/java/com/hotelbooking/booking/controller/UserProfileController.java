package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.request.UpdateMyProfileRequest;
import com.hotelbooking.booking.dto.response.UserResponse;
import com.hotelbooking.booking.service.UserProfileService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Профиль и пароль")
@RestController
@RequestMapping("/api/users/me")
public class UserProfileController {

    private final UserProfileService profileService;

    public UserProfileController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public UserResponse getMyProfile(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return profileService.getMyProfile(
                Long.valueOf(jwt.getSubject())
        );
    }

    @PatchMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public UserResponse updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        return profileService.updateMyProfile(
                Long.valueOf(jwt.getSubject()),
                request
        );
    }
}