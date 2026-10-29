package com.hotelbooking.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import java.util.List;

@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${app.jwt.keystore.path}") String path,
            @Value("${app.jwt.keystore.password}") String password,
            @Value("${app.jwt.keystore.alias}") String alias,
            @Value("${app.jwt.issuer}") String issuer
    ) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try (InputStream input = Files.newInputStream(Path.of(path))) {
            keyStore.load(input, password.toCharArray());
        }

        Certificate certificate = keyStore.getCertificate(alias);

        if (certificate == null ||
                !(certificate.getPublicKey() instanceof RSAPublicKey publicKey)) {
            throw new IllegalStateException("JWT public key not found");
        }

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withPublicKey(publicKey).build();

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        new JwtClaimValidator<List<String>>(
                                "aud",
                                aud -> aud != null && aud.contains("booking-service")
                        ),
                        new JwtClaimValidator<String>(
                                "role",
                                role -> "USER".equals(role) || "ADMIN".equals(role)
                        )
                )
        );

        return decoder;
    }
}