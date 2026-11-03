package com.hotelbooking.gateway.config;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class GatewaySecurityConfig {

  @Bean
  public ReactiveJwtDecoder jwtDecoder(@Value("classpath:jwt-signing.crt") Resource resource)
      throws Exception {

    X509Certificate certificate;

    try (var input = resource.getInputStream()) {
      certificate =
          (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
    }

    RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();

    NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();

    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer("hotel-booking"),
            new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && aud.contains("api-gateway")),
            new JwtClaimValidator<String>(
                "role", role -> "USER".equals(role) || "ADMIN".equals(role))));

    return decoder;
  }

  @Bean
  public UrlBasedCorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(
        List.of(
            "http://localhost:8081",
            "http://localhost:8082",
            "http://localhost:5173",
            "http://127.0.0.1:5173"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key", "X-Trace-Id"));
    configuration.setExposedHeaders(List.of("X-Trace-Id"));
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  @Bean
  public SecurityWebFilterChain securityFilterChain(
      ServerHttpSecurity http,
      UrlBasedCorsConfigurationSource corsConfigurationSource,
      GatewaySecurityErrorHandler securityErrorHandler) {
    return http.cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(
            auth ->
                auth.pathMatchers(
                        HttpMethod.POST,
                        "/api/rooms/*/confirm-availability",
                        "/api/rooms/*/release")
                    .denyAll()
                    .pathMatchers(HttpMethod.GET, "/api/users/token-status")
                    .denyAll()
                    .pathMatchers(HttpMethod.POST, "/api/users/register", "/api/users/login")
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(securityErrorHandler)
                    .accessDeniedHandler(securityErrorHandler))
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .authenticationEntryPoint(securityErrorHandler)
                    .accessDeniedHandler(securityErrorHandler)
                    .jwt(jwt -> {}))
        .build();
  }
}
