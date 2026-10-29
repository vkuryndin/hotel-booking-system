package com.hotelbooking.booking.service;

import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Tests real RS256 signing/verification without application keystore or ENV secrets. */
class JwtServiceTest {

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;
    private static NimbusJwtDecoder decoder;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        publicKey = (RSAPublicKey) pair.getPublic();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("hotel-booking"));
    }

    private JwtService service(String issuer, Duration ttl) {
        return new JwtService(NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build(), issuer, ttl);
    }

    private AppUser user(Role role) {
        AppUser user = new AppUser("test@example.com", "hashed-password", role);
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }

    @Test
    void signedTokenHasExpectedSubjectIssuerRoleAndIdentity() {
        AppUser user = user(Role.USER);
        Jwt jwt = decoder.decode(service("hotel-booking", Duration.ofHours(1)).generateAccessToken(user));

        assertEquals("42", jwt.getSubject());
        assertEquals("hotel-booking", jwt.getClaimAsString("iss"));
        assertEquals("USER", jwt.getClaimAsString("role"));
        assertEquals(user.getTokenIdentity(), jwt.getClaimAsString("tokenIdentity"));
        assertEquals(user.getTokenVersion(), ((Number) jwt.getClaim("tokenVersion")).longValue());
        assertEquals("RS256", jwt.getHeaders().get("alg"));
    }

    @Test
    void adminTokenIncludesAdminRole() {
        Jwt jwt = decoder.decode(service("hotel-booking", Duration.ofHours(1))
                .generateAccessToken(user(Role.ADMIN)));
        assertEquals("ADMIN", jwt.getClaimAsString("role"));
    }

    @Test
    void lifetimeIsOneHourAndReportedInSeconds() {
        JwtService service = service("hotel-booking", Duration.ofHours(1));
        Jwt jwt = decoder.decode(service.generateAccessToken(user(Role.USER)));
        assertEquals(3600L, service.getExpiresInSeconds());
        assertEquals(3600L, Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair other = generator.generateKeyPair();
        JwtService foreign = new JwtService(
                NimbusJwtEncoder.withKeyPair((RSAPublicKey) other.getPublic(),
                        (RSAPrivateKey) other.getPrivate()).build(),
                "hotel-booking", Duration.ofHours(1));
        assertThrows(JwtException.class, () -> decoder.decode(foreign.generateAccessToken(user(Role.USER))));
    }

    @Test
    void modifiedPayloadIsRejectedBySignatureValidation() {
        String token = service("hotel-booking", Duration.ofHours(1)).generateAccessToken(user(Role.USER));
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        // Modify the signed payload without updating the signature.
        char first = parts[1].charAt(0);
        parts[1] = (first == 'A' ? 'B' : 'A') + parts[1].substring(1);
        String tampered = String.join(".", parts);
        assertThrows(JwtException.class, () -> decoder.decode(tampered));
    }

    @Test
    void wrongIssuerIsRejected() {
        String token = service("wrong-issuer", Duration.ofHours(1)).generateAccessToken(user(Role.USER));
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void expiredTokenIsRejected() {
        Instant now = Instant.now();
        JwtClaimsSet expiredClaims = JwtClaimsSet.builder()
                .issuer("hotel-booking")
                .subject("42")
                .issuedAt(now.minus(Duration.ofHours(2)))
                .expiresAt(now.minus(Duration.ofHours(1)))
                .build();

        String token = NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build()
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256).build(), expiredClaims))
                .getTokenValue();

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void roleAndTokenVersionChangeAreEmbeddedInNewToken() {
        AppUser user = user(Role.USER);
        Jwt before = decoder.decode(service("hotel-booking", Duration.ofHours(1)).generateAccessToken(user));
        user.changeRole(Role.ADMIN);
        Jwt after = decoder.decode(service("hotel-booking", Duration.ofHours(1)).generateAccessToken(user));

        assertEquals("USER", before.getClaimAsString("role"));
        assertEquals("ADMIN", after.getClaimAsString("role"));
        assertEquals(((Number) before.getClaim("tokenVersion")).longValue() + 1,
                ((Number) after.getClaim("tokenVersion")).longValue());
        // Revocation of an otherwise valid old signature is enforced by UserTokenStateWebConfig.
        assertEquals("USER", decoder.decode(before.getTokenValue()).getClaimAsString("role"));
    }
}
