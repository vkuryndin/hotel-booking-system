package com.hotelbooking.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GatewaySecretFilterTest {
    @Test
    void forgedClientSecretIsOverwrittenBeforeForwarding() {
        GatewaySecretFilter filter = new GatewaySecretFilter("trusted-secret");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/hotels")
                        .header(GatewaySecretFilter.HEADER, "attacker-secret")
                        .build());
        AtomicReference<String> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = current -> {
            forwarded.set(current.getRequest().getHeaders().getFirst(GatewaySecretFilter.HEADER));
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        assertEquals("trusted-secret", forwarded.get());
    }

    @Test
    void gatewaySuppliesSecretWhenClientSentNone() {
        GatewaySecretFilter filter = new GatewaySecretFilter("trusted-secret");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/rooms").build());
        AtomicReference<String> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = current -> {
            forwarded.set(current.getRequest().getHeaders().getFirst(GatewaySecretFilter.HEADER));
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        assertEquals("trusted-secret", forwarded.get());
    }
    @Test
    void forgedVerifiedIpIsRemovedAndActualPeerIsUsed() {
        var filter = new GatewaySecretFilter("trusted-secret");
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/login")
                        .remoteAddress(new java.net.InetSocketAddress("10.0.0.17", 1234))
                        .header("X-Verified-Client-IP", "192.0.2.123")
                        .build());
        var forwarded = new AtomicReference<String>();
        GatewayFilterChain chain = current -> {
            forwarded.set(current.getRequest().getHeaders().getFirst("X-Verified-Client-IP"));
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        assertEquals("10.0.0.17", forwarded.get());
    }
}
