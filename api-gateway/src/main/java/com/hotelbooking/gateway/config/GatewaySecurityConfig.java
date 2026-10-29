package com.hotelbooking.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import java.util.List;

@Configuration
public class GatewaySecurityConfig {

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("classpath:jwt-signing.crt") Resource resource
    ) throws Exception {

        X509Certificate certificate;

        try (var input = resource.getInputStream()) {
            certificate = (X509Certificate)
                    CertificateFactory.getInstance("X.509")
                            .generateCertificate(input);
        }

        RSAPublicKey publicKey =
                (RSAPublicKey) certificate.getPublicKey();

        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer("hotel-booking"),
                        new JwtClaimValidator<List<String>>(
                                "aud",
                                aud -> aud != null && aud.contains("api-gateway")
                        ),
                        new JwtClaimValidator<String>(
                                "role",
                                role -> "USER".equals(role) || "ADMIN".equals(role)
                        )
                )
        );

        return decoder;
    }

    @Bean
    public SecurityWebFilterChain securityFilterChain(
            ServerHttpSecurity http
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/rooms/*/confirm-availability",
                                "/api/rooms/*/release"
                        ).denyAll()
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/users/token-status"
                        ).denyAll()
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/users/register",
                                "/api/users/login"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(
                        jwt -> {}
                ))
                .build();
    }
}