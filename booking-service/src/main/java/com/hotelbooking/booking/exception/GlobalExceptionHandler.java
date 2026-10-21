package com.hotelbooking.booking.exception;

import com.hotelbooking.booking.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(
            EmailAlreadyRegisteredException ex,
            HttpServletRequest request) {

        return buildError(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED",
                ex.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()
                )
        );

        return buildError(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Invalid request data",
                request,
                errors
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        return buildError(
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                ex.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request) {

        return buildError(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid email or password",
                request,
                Map.of()
        );
    }

    private ResponseEntity<ErrorResponse> buildError(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                MDC.get("traceId"),
                fieldErrors
        );

        return ResponseEntity.status(status).body(response);
    }
}