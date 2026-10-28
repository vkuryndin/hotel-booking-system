package com.hotelbooking.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceWebFilter implements WebFilter {

    public static final String HEADER = "X-Trace-Id";

    private static final Logger log =
            LoggerFactory.getLogger(TraceWebFilter.class);

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain
    ) {
        String traceId = normalize(
                exchange.getRequest().getHeaders().getFirst(HEADER)
        );

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(HEADER, traceId))
                .build();

        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HEADER, traceId);
            return Mono.empty();
        });

        log.info(
                "HTTP IN traceId={} method={} path={}",
                traceId,
                request.getMethod(),
                request.getPath().value()
        );

        ServerWebExchange updatedExchange = exchange.mutate()
                .request(request)
                .build();

        return chain.filter(updatedExchange)
                .doFinally(signal -> log.info(
                        "HTTP OUT traceId={} method={} path={} status={} signal={}",
                        traceId,
                        request.getMethod(),
                        request.getPath().value(),
                        exchange.getResponse().getStatusCode(),
                        signal
                ));
    }

    private static String normalize(String supplied) {
        if (supplied != null) {
            try {
                UUID uuid = UUID.fromString(supplied);

                if (uuid.toString().equalsIgnoreCase(supplied)) {
                    return uuid.toString();
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid traceId: generate a new UUID.
            }
        }

        return UUID.randomUUID().toString();
    }
}