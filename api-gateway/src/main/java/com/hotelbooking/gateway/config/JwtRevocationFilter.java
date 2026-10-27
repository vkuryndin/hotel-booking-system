
package com.hotelbooking.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Component
public class JwtRevocationFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;
    private final String secret;

    public JwtRevocationFilter(
            @LoadBalanced WebClient.Builder builder,
            @Value("${GATEWAY_INTERNAL_SECRET}") String secret
    ) {
        this.webClient = builder.build();

        if (secret.isBlank()) {
            throw new IllegalArgumentException(
                    "GATEWAY_INTERNAL_SECRET must be set");
        }

        this.secret = secret;
    }

    @Override
    public int getOrder() {
        return -10;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        String path =
                exchange.getRequest().getPath().value();
        HttpMethod method =
                exchange.getRequest().getMethod();

        if (HttpMethod.POST.equals(method)
                && (path.equals("/api/users/login")
                || path.equals("/api/users/register"))) {
            return chain.filter(exchange);
        }

        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(principal -> {

                    if (principal.isEmpty()) {
                        return reject(exchange,
                                HttpStatus.UNAUTHORIZED,
                                "INVALID_TOKEN");
                    }

                    var jwt = principal.get().getToken();
                    String role = jwt.getClaimAsString("role");

                    if (!"USER".equals(role)
                            && !"ADMIN".equals(role)) {
                        return reject(exchange,
                                HttpStatus.FORBIDDEN,
                                "FORBIDDEN");
                    }

                    return webClient.get()
                            .uri("http://booking-service/api/users/token-status")
                            .headers(headers -> {
                                headers.setBearerAuth(
                                        jwt.getTokenValue());
                                headers.set(
                                        GatewaySecretFilter.HEADER,
                                        secret);
                            })
                            .exchangeToMono(response ->
                                    Mono.just(
                                            response.statusCode().value()))
                            .timeout(Duration.ofSeconds(3))
                            .onErrorReturn(503)
                            .flatMap(status -> {

                                boolean changePassword =
                                        HttpMethod.POST.equals(method)
                                                && path.equals(
                                                "/api/users/me/change-password");

                                if (status == 204
                                        || (status == 403 && changePassword)) {
                                    return chain.filter(exchange);
                                }

                                if (status == 401) {
                                    return reject(exchange,
                                            HttpStatus.UNAUTHORIZED,
                                            "TOKEN_REVOKED");
                                }

                                if (status == 403) {
                                    return reject(exchange,
                                            HttpStatus.FORBIDDEN,
                                            "PASSWORD_CHANGE_REQUIRED");
                                }

                                return reject(exchange,
                                        HttpStatus.SERVICE_UNAVAILABLE,
                                        "AUTH_SERVICE_UNAVAILABLE");
                            });
                });
    }

    private Mono<Void> reject(
            ServerWebExchange exchange,
            HttpStatus status,
            String code
    ) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(
                MediaType.APPLICATION_JSON);

        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(
                    HttpHeaders.WWW_AUTHENTICATE,
                    "Bearer");
        }

        byte[] body =
                ("{\"code\":\"" + code + "\"}")
                        .getBytes(StandardCharsets.UTF_8);

        return response.writeWith(
                Mono.just(
                        response.bufferFactory().wrap(body)));
    }
}
