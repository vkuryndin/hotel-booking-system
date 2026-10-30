package com.hotelbooking.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.reactive.function.client.ClientRequest;

import static org.junit.jupiter.api.Assertions.*;

/** Gateway token-state HTTP response mapping, using an in-memory WebClient (no Eureka needed). */
class JwtRevocationFilterTest {
    private static final String SECRET = "gateway-test-secret";

    private record CallResult(MockServerWebExchange exchange, int forwarded,
                              int authChecks, ClientRequest request) {}

    private CallResult run(HttpMethod method, String path, String role,
                           boolean principalPresent, int tokenStatus) {
        AtomicInteger checks = new AtomicInteger();
        AtomicInteger forwarded = new AtomicInteger();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            checks.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.valueOf(tokenStatus)).build());
        });

        JwtRevocationFilter filter = new JwtRevocationFilter(builder, SECRET);
        MockServerHttpRequest request =
                MockServerHttpRequest.method(method, path).build();

        MockServerWebExchange exchange;

        if (principalPresent) {
            Jwt jwt = Jwt.withTokenValue("valid-test-jwt")
                    .header("alg", "RS256")
                    .subject("42")
                    .claim("role", role)
                    .build();

            exchange = MockServerWebExchange.builder(request)
                    .principal(new JwtAuthenticationToken(jwt))
                    .build();
        } else {
            exchange = MockServerWebExchange.from(request);
        }

        GatewayFilterChain chain = current -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return new CallResult(exchange, forwarded.get(), checks.get(), captured.get());
    }

    @Test
    void approvedJwtStatusForwardsHotelRequest() {
        CallResult result = run(HttpMethod.GET, "/api/hotels", "USER", true, 204);
        assertEquals(1, result.forwarded());
        assertEquals(1, result.authChecks());
        assertNotNull(result.request());
        assertEquals("http://booking-service/api/users/token-status", result.request().url().toString());
        assertEquals("Bearer valid-test-jwt", result.request().headers().getFirst("Authorization"));
        assertEquals(SECRET, result.request().headers().getFirst("X-Gateway-Internal-Secret"));
    }

    @Test
    void revokedJwtIsRejectedAsUnauthorized() {
        CallResult result = run(HttpMethod.GET, "/api/rooms", "USER", true, 401);
        assertEquals(0, result.forwarded());
        assertEquals(HttpStatus.UNAUTHORIZED, result.exchange().getResponse().getStatusCode());
        assertEquals("Bearer", result.exchange().getResponse().getHeaders().getFirst("WWW-Authenticate"));
    }

    @Test
    void forcedPasswordChangeBlocksOrdinaryRequests() {
        CallResult result = run(HttpMethod.GET, "/api/hotels", "USER", true, 403);
        assertEquals(0, result.forwarded());
        assertEquals(HttpStatus.FORBIDDEN, result.exchange().getResponse().getStatusCode());
    }

    @Test
    void forcedPasswordChangeAllowsPasswordChangeEndpoint() {
        CallResult result = run(HttpMethod.POST, "/api/users/me/change-password", "USER", true, 403);
        assertEquals(1, result.forwarded());
        assertEquals(1, result.authChecks());
    }

    @Test
    void bookingServiceOutageFailsClosed() {
        CallResult result = run(HttpMethod.GET, "/api/hotels", "USER", true, 503);
        assertEquals(0, result.forwarded());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.exchange().getResponse().getStatusCode());
    }

    @Test
    void noAuthenticationReturnsUnauthorizedWithoutBackendCheck() {
        CallResult result = run(HttpMethod.GET, "/api/hotels", null, false, 204);
        assertEquals(0, result.forwarded());
        assertEquals(0, result.authChecks());
        assertEquals(HttpStatus.UNAUTHORIZED, result.exchange().getResponse().getStatusCode());
    }

    @Test
    void unrecognizedRoleCannotReachHotelOrTokenStatus() {
        CallResult result = run(HttpMethod.GET, "/api/hotels", "SERVICE", true, 204);
        assertEquals(0, result.forwarded());
        assertEquals(0, result.authChecks());
        assertEquals(HttpStatus.FORBIDDEN, result.exchange().getResponse().getStatusCode());
    }

    @Test
    void loginIsNotBlockedByMissingJwtOrBookingStateCheck() {
        CallResult result = run(HttpMethod.POST, "/api/users/login", null, false, 503);
        assertEquals(1, result.forwarded());
        assertEquals(0, result.authChecks());
    }

    @Test
    void registrationIsNotBlockedByMissingJwtOrBookingStateCheck() {
        CallResult result = run(HttpMethod.POST, "/api/users/register", null, false, 503);
        assertEquals(1, result.forwarded());
        assertEquals(0, result.authChecks());
    }

    @Test
    void adminRoleIsAccepted() {
        CallResult result = run(HttpMethod.GET, "/api/users", "ADMIN", true, 204);
        assertEquals(1, result.forwarded());
        assertEquals(1, result.authChecks());
    }
    @Test
    void simultaneousRequestsWithSameTokenShareStatusCall() {
        var checks = new AtomicInteger();
        var sharedResponse = reactor.core.publisher.Sinks.<ClientResponse>one();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            checks.incrementAndGet();
            return sharedResponse.asMono();
        });
        var filter = new JwtRevocationFilter(builder, SECRET);
        Jwt jwt = Jwt.withTokenValue("shared-token")
                .header("alg", "RS256").subject("42").claim("role", "USER").build();
        var request = MockServerHttpRequest.get("/api/hotels").build();
        var first = MockServerWebExchange.builder(request)
                .principal(new JwtAuthenticationToken(jwt)).build();
        var second = MockServerWebExchange.builder(request)
                .principal(new JwtAuthenticationToken(jwt)).build();
        var forwarded = new AtomicInteger();
        GatewayFilterChain chain = current -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        };
        var both = Mono.when(filter.filter(first, chain), filter.filter(second, chain));
        var running = both.subscribe();
        assertEquals(1, checks.get());
        sharedResponse.tryEmitValue(ClientResponse.create(HttpStatus.NO_CONTENT).build());
        assertEquals(2, forwarded.get());
        running.dispose();
    }

}
