package com.hotelbooking.booking.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** Checks internal Booking -> Hotel SERVICE tokens using real RS256 signatures. */
class ServiceTokenProviderTest {
    private static ServiceTokenProvider serviceTokens;
    private static NimbusJwtDecoder decoder;

    @BeforeAll
    static void setup() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
        RSAPrivateKey priv = (RSAPrivateKey) pair.getPrivate();
        serviceTokens = new ServiceTokenProvider(NimbusJwtEncoder.withKeyPair(pub, priv).build());
        decoder = NimbusJwtDecoder.withPublicKey(pub).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("hotel-booking"));
    }

    @Test
    void serviceTokenUsesCorrectIssuerSubjectAudienceAndRole() {
        Jwt token = decoder.decode(serviceTokens.generateToken());
        assertEquals("hotel-booking", token.getClaimAsString("iss"));
        assertEquals("booking-service", token.getSubject());
        assertEquals("SERVICE", token.getClaimAsString("role"));
        assertEquals(1, token.getAudience().size());
        assertTrue(token.getAudience().contains("hotel-service"));
        assertEquals("RS256", token.getHeaders().get("alg"));
    }

    @Test
    void serviceTokenExpiresAfterOneMinute() {
        Jwt jwt = decoder.decode(serviceTokens.generateToken());
        assertEquals(60L, Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
    }

    @Test
    void serviceTokenDoesNotHaveUserTokenVersionOrIdentity() {
        Jwt jwt = decoder.decode(serviceTokens.generateToken());
        assertNull(jwt.getClaim("tokenVersion"));
        assertNull(jwt.getClaim("tokenIdentity"));
    }
}
