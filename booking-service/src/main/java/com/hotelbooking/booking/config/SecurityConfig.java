package com.hotelbooking.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import com.hotelbooking.booking.security.SecurityExceptionHandler;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityExceptionHandler securityExceptionHandler
    ) throws Exception {

        JwtGrantedAuthoritiesConverter rolesConverter =
                new JwtGrantedAuthoritiesConverter();

        rolesConverter.setAuthoritiesClaimName("role");
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtConverter =
                new JwtAuthenticationConverter();

        jwtConverter.setJwtGrantedAuthoritiesConverter(rolesConverter);

        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users/register",
                                "/api/users/login"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                );

        return http.build();
    }
}