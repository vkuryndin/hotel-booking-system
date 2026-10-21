package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.request.RegisterRequest;
import com.hotelbooking.booking.dto.response.UserResponse;
import com.hotelbooking.booking.service.RegistrationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        UserResponse response = registrationService.register(request);

        URI location = URI.create("/api/users/" + response.id());

        return ResponseEntity.created(location).body(response);
    }
}