package com.hotelbooking.booking.exception;

import com.hotelbooking.booking.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import com.hotelbooking.booking.exception.RequestInProgressException;

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
    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomNotFound(
            RoomNotFoundException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse(
                        Instant.now(), 404, "ROOM_NOT_FOUND",
                        ex.getMessage(), request.getRequestURI(),
                        null, null
                )
        );
    }

    @ExceptionHandler(RoomUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRoomUnavailable(
            RoomUnavailableException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ErrorResponse(
                        Instant.now(), 409, "ROOM_UNAVAILABLE",
                        ex.getMessage(), request.getRequestURI(),
                        null, null
                )
        );
    }

    @ExceptionHandler(HotelServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleHotelServiceUnavailable(
            HotelServiceUnavailableException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                new ErrorResponse(
                        Instant.now(), 503, "HOTEL_SERVICE_UNAVAILABLE",
                        ex.getMessage(), request.getRequestURI(),
                        null, null
                )
        );
    }
    @ExceptionHandler(RoomAlreadyBookedException.class)
    public ResponseEntity<ErrorResponse> handleRoomAlreadyBooked(
            RoomAlreadyBookedException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ErrorResponse(
                        Instant.now(),
                        409,
                        "ROOM_ALREADY_BOOKED",
                        ex.getMessage(),
                        request.getRequestURI(),
                        null,
                        null
                )
        );
    }
    @ExceptionHandler(BookingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleBookingNotFound(
            BookingNotFoundException ex,
            HttpServletRequest request
    ) {
        return new ErrorResponse(
                Instant.now(),
                404,
                "BOOKING_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                null,
                null
        );
    }
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return new ErrorResponse(
                Instant.now(),
                403,
                "ACCESS_DENIED",
                "You do not have permission to access this resource",
                request.getRequestURI(),
                null,
                null
        );
    }
    @ExceptionHandler(InvalidBookingStatusException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInvalidBookingStatus(
            InvalidBookingStatusException ex,
            HttpServletRequest request
    ) {
        return new ErrorResponse(
                Instant.now(),
                409,
                "INVALID_BOOKING_STATUS",
                ex.getMessage(),
                request.getRequestURI(),
                null,
                null
        );
    }
    @ExceptionHandler(BookingSagaUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleSagaUnavailable(
            BookingSagaUnavailableException ex,
            HttpServletRequest request
    ) {
        return new ErrorResponse(
                Instant.now(),
                503,
                "BOOKING_SAGA_UNAVAILABLE",
                ex.getMessage(),
                request.getRequestURI(),
                null,
                null
        );
    }
    @ExceptionHandler(IdempotencyKeyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIdempotencyConflict(
            IdempotencyKeyConflictException ex,
            HttpServletRequest request
    ) {
        return new ErrorResponse(
                Instant.now(),
                409,
                "IDEMPOTENCY_KEY_CONFLICT",
                ex.getMessage(),
                request.getRequestURI(),
                null,
                null
        );
    }
    @ExceptionHandler(RequestInProgressException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleRequestInProgress(
            RequestInProgressException ex,
            HttpServletRequest request
    ) {
        return new ErrorResponse(
                Instant.now(),
                409,
                "REQUEST_IN_PROGRESS",
                ex.getMessage(),
                request.getRequestURI(),
                null,
                null
        );
    }


    @ExceptionHandler(HotelNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleHotelNotFound(
            HotelNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.NOT_FOUND,
                "HOTEL_NOT_FOUND",
                ex.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(NoAvailableRoomsException.class)
    public ResponseEntity<ErrorResponse> handleNoAvailableRooms(
            NoAvailableRoomsException ex,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.CONFLICT,
                "NO_AVAILABLE_ROOMS",
                ex.getMessage(),
                request,
                Map.of()
        );
    }
    @ExceptionHandler(UsernameAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUsername(
            UsernameAlreadyRegisteredException ex,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.CONFLICT,
                "USERNAME_ALREADY_REGISTERED",
                ex.getMessage(),
                request,
                Map.of()
        );
    }
}