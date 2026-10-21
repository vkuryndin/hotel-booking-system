package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.request.LoginRequest;
import com.hotelbooking.booking.dto.response.LoginResponse;
import com.hotelbooking.booking.service.LoginService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class LoginController {

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = loginService.login(request);

        return ResponseEntity.ok(response);
    }
}