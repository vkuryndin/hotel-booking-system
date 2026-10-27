package com.hotelbooking.booking.dto.response;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        boolean passwordChangeRequired
) {
}