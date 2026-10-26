package com.hotelbooking.booking.security;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ServiceTokenProvider {

    private final JwtEncoder jwtEncoder;

    public ServiceTokenProvider(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String generateToken() {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("hotel-booking")
                .subject("booking-service")
                .audience(List.of("hotel-service"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("role", "SERVICE")
                .build();

        JwsHeader header = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}