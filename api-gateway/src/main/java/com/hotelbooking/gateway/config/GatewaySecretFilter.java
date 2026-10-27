
package com.hotelbooking.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewaySecretFilter implements GlobalFilter, Ordered {

    public static final String HEADER =
            "X-Gateway-Internal-Secret";

    private final String secret;

    public GatewaySecretFilter(
            @Value("${GATEWAY_INTERNAL_SECRET}") String secret
    ) {
        if (secret.isBlank()) {
            throw new IllegalArgumentException(
                    "GATEWAY_INTERNAL_SECRET must be set");
        }
        this.secret = secret;
    }

    @Override
    public int getOrder() {
        return -20;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        var request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(HEADER, secret))
                .build();

        return chain.filter(
                exchange.mutate().request(request).build()
        );
    }
}
