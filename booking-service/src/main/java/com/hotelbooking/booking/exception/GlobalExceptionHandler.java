package com.hotelbooking.booking.exception;

import com.hotelbooking.booking.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Every MVC error is built in one place, with the same JSON fields and traceId.
    private ResponseEntity<ErrorResponse> buildError(
            HttpStatusCode status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                MDC.get("traceId"),
                fieldErrors
        );
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status, String code, String message,
            HttpServletRequest request
    ) {
        return buildError(status, code, message, request, Map.of());
    }

    // ---- Existing business contracts: statuses and error codes are unchanged ----

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(
            EmailAlreadyRegisteredException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", ex.getMessage(), request);
    }

    @ExceptionHandler(UsernameAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUsername(
            UsernameAlreadyRegisteredException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "USERNAME_ALREADY_REGISTERED", ex.getMessage(), request);
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomNotFound(
            RoomNotFoundException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(RoomUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRoomUnavailable(
            RoomUnavailableException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "ROOM_UNAVAILABLE", ex.getMessage(), request);
    }

    @ExceptionHandler(HotelServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleHotelServiceUnavailable(
            HotelServiceUnavailableException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "HOTEL_SERVICE_UNAVAILABLE", ex.getMessage(), request);
    }

    @ExceptionHandler(RoomAlreadyBookedException.class)
    public ResponseEntity<ErrorResponse> handleRoomAlreadyBooked(
            RoomAlreadyBookedException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "ROOM_ALREADY_BOOKED", ex.getMessage(), request);
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBookingNotFound(
            BookingNotFoundException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(HotelNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleHotelNotFound(
            HotelNotFoundException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "HOTEL_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(NoAvailableRoomsException.class)
    public ResponseEntity<ErrorResponse> handleNoAvailableRooms(
            NoAvailableRoomsException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "NO_AVAILABLE_ROOMS", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to access this resource", request);
    }

    @ExceptionHandler(InvalidBookingStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBookingStatus(
            InvalidBookingStatusException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "INVALID_BOOKING_STATUS", ex.getMessage(), request);
    }

    @ExceptionHandler(BookingSagaUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleSagaUnavailable(
            BookingSagaUnavailableException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_SAGA_UNAVAILABLE", ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
            IdempotencyKeyConflictException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", ex.getMessage(), request);
    }

    @ExceptionHandler(RequestInProgressException.class)
    public ResponseEntity<ErrorResponse> handleRequestInProgress(
            RequestInProgressException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password", request);
    }

    @ExceptionHandler(BookingRequestValidationException.class)
    public ResponseEntity<ErrorResponse> handleBookingRequestValidation(
            BookingRequestValidationException ex, HttpServletRequest request
    ) {
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Invalid request data", request, ex.getFieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage())
        );
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Invalid request data", request, errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidArgument(
            IllegalArgumentException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), request);
    }

    // ---- Standard HTTP/MVC errors not previously handled consistently ----

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(
            HttpMessageNotReadableException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_JSON",
                "Malformed or unreadable request body", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_REQUEST_HEADER",
                "Required header is missing: " + ex.getHeaderName(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_REQUEST_PARAMETER",
                "Required parameter is missing: " + ex.getParameterName(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleParameterTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETER",
                "Invalid parameter: " + ex.getName(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "HTTP method not supported for this endpoint", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Unsupported request content type", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleMissingResource(
            NoResourceFoundException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found", request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleMissingHandler(
            NoHandlerFoundException ex, HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found", request);
    }

    // Some existing user/admin services throw ResponseStatusException.
    // Preserve their HTTP statuses; do not accidentally turn them into 500.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request
    ) {
        HttpStatusCode status = ex.getStatusCode();
        String code = switch (status.value()) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 409 -> "CONFLICT";
            default -> "HTTP_ERROR";
        };
        String message = ex.getReason() == null || ex.getReason().isBlank()
                ? "Request failed" : ex.getReason();
        return buildError(status, code, message, request, Map.of());
    }

    // Stale writes must not overwrite an administrator's role or token revocation.
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleOptimisticLockConflict(
            Exception ex, HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "User was modified concurrently; reload and retry", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request
    ) {
        log.error("Unhandled booking-service error: method={} path={} traceId={}",
                request.getMethod(), request.getRequestURI(), MDC.get("traceId"), ex);
        // Never expose stack traces, database details or secrets to API clients.
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request);
    }
}
