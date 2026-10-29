package com.hotelbooking.hotel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import java.util.List;
@Configuration
public class JwtDecoderConfig {

        @Bean
    public JwtDecoder jwtDecoder(
            @Value("classpath:jwt-signing.crt") Resource userCertificate,
            @Value("classpath:service-jwt-signing.crt") Resource serviceCertificate,
            @Value("${app.jwt.issuer}") String issuer
    ) throws Exception {

        NimbusJwtDecoder userDecoder = createDecoder(userCertificate);

        userDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        new JwtClaimValidator<List<String>>(
                                "aud",
                                aud -> aud != null && aud.contains("hotel-service")
                        ),
                        new JwtClaimValidator<String>(
                                "role",
                                role -> "USER".equals(role) || "ADMIN".equals(role)
                        )
                )
        );

        NimbusJwtDecoder serviceDecoder = createDecoder(serviceCertificate);

        serviceDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        new JwtClaimValidator<List<String>>(
                                "aud",
                                aud -> aud != null && aud.contains("hotel-service")
                        ),
                        new JwtClaimValidator<String>(
                                "role",
                                role -> "SERVICE".equals(role)
                        ),
                        new JwtClaimValidator<String>(
                                "sub",
                                sub -> "booking-service".equals(sub)
                        )
                )
        );

        return token -> {
            try {
                return userDecoder.decode(token);
            } catch (JwtException ex) {
                return serviceDecoder.decode(token);
            }
        };
    }
    private NimbusJwtDecoder createDecoder(Resource resource) throws Exception {

        CertificateFactory factory = CertificateFactory.getInstance("X.509");

        X509Certificate certificate;

        try (InputStream input = resource.getInputStream()) {
            certificate = (X509Certificate) factory.generateCertificate(input);
        }

        if (!(certificate.getPublicKey() instanceof RSAPublicKey publicKey)) {
            throw new IllegalStateException("Invalid JWT certificate");
        }

        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}