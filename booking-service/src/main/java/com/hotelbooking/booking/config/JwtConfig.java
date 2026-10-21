package com.hotelbooking.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(
            @Value("${app.jwt.keystore.path}") String path,
            @Value("${app.jwt.keystore.password}") String password,
            @Value("${app.jwt.keystore.alias}") String alias
    ) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        char[] passwordChars = password.toCharArray();

        try (InputStream input = Files.newInputStream(Path.of(path))) {
            keyStore.load(input, passwordChars);
        }

        Key key = keyStore.getKey(alias, passwordChars);
        Certificate certificate = keyStore.getCertificate(alias);

        if (!(key instanceof RSAPrivateKey privateKey)
                || certificate == null
                || !(certificate.getPublicKey() instanceof RSAPublicKey publicKey)) {
            throw new IllegalStateException("Invalid RSA signing key");
        }

        return NimbusJwtEncoder
                .withKeyPair(publicKey, privateKey)
                .build();
    }
}