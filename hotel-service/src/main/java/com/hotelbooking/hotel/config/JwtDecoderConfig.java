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

@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("classpath:jwt-signing.crt") Resource certificateResource,
            @Value("${app.jwt.issuer}") String issuer
    ) throws Exception {

        CertificateFactory factory = CertificateFactory.getInstance("X.509");

        X509Certificate certificate;

        try (InputStream input = certificateResource.getInputStream()) {
            certificate = (X509Certificate) factory.generateCertificate(input);
        }

        RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(publicKey)
                .build();

        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(issuer)
        );

        return decoder;
    }
}