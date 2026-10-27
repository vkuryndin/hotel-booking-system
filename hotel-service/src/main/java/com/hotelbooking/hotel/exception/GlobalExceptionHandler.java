package com.hotelbooking.hotel.exception;

import com.hotelbooking.hotel.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import java.util.Locale;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        return buildError(
                HttpStatus.BAD_REQUEST,
                "INVALID_JSON",
                "Invalid request body",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error", ex);

        return buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(HotelNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleHotelNotFound(
            HotelNotFoundException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse(
                        Instant.now(),
                        404,
                        "HOTEL_NOT_FOUND",
                        ex.getMessage(),
                        request.getRequestURI(),
                        null,
                        null
                )
        );
    }

    @ExceptionHandler(RoomAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleRoomAlreadyExists(
            RoomAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ErrorResponse(
                        Instant.now(),
                        409,
                        "ROOM_ALREADY_EXISTS",
                        ex.getMessage(),
                        request.getRequestURI(),
                        null,
                        null
                )
        );
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomNotFound(
            RoomNotFoundException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse(
                        Instant.now(),
                        404,
                        "ROOM_NOT_FOUND",
                        ex.getMessage(),
                        request.getRequestURI(),
                        null,
                        null
                )
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
    @ExceptionHandler(HotelAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleHotelAlreadyExists(
            HotelAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        return new ErrorResponse(
                Instant.now(),
                409,
                "HOTEL_ALREADY_EXISTS",
                ex.getMessage(),
                request.getRequestURI(),
                null,
                null
        );
    }
    @ExceptionHandler(RoomReservationConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleRoomReservationConflict(
            RoomReservationConflictException ex,
            HttpServletRequest request
    ) {
        return new ErrorResponse(
                Instant.now(),
                409,
                "ROOM_RESERVATION_CONFLICT",
                ex.getMessage(),
                request.getRequestURI(),
                null,
                null
        );
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        boolean nightConflict = false;

        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException c
                    && c.getConstraintName() != null
                    && c.getConstraintName().toLowerCase(Locale.ROOT)
                    .contains("uk_room_night")) {
                nightConflict = true;
                break;
            }
        }

        HttpStatus status = nightConflict
                ? HttpStatus.CONFLICT
                : HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(status).body(
                new ErrorResponse(
                        Instant.now(),
                        status.value(),
                        nightConflict ? "ROOM_RESERVATION_CONFLICT" : "DATA_INTEGRITY_ERROR",
                        nightConflict
                                ? "Room is already booked for selected dates"
                                : "Database integrity constraint violated",
                        request.getRequestURI(),
                        null,
                        null
                )
        );
    }
    @ExceptionHandler(ResourceInUseException.class)
    public ResponseEntity<ErrorResponse> handleResourceInUse(
            ResourceInUseException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ErrorResponse(
                        Instant.now(),
                        409,
                        ex.getCode(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        null,
                        null
                )
        );
    }
}