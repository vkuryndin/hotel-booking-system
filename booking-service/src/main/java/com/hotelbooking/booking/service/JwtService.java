package com.hotelbooking.booking.service;

import com.hotelbooking.booking.entity.AppUser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration tokenTtl;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.access-token-ttl}") Duration tokenTtl) {

        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.tokenTtl = tokenTtl;
    }

    public String generateAccessToken(AppUser user) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(tokenTtl))
                .claim("role", user.getRole().name())
                .claim("tokenVersion", user.getTokenVersion())
                .claim("tokenIdentity", user.getTokenIdentity())
                .build();

        JwsHeader header = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    public long getExpiresInSeconds() {
        return tokenTtl.toSeconds();
    }
}