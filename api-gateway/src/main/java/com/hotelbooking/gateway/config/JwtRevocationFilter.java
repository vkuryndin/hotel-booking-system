package com.hotelbooking.gateway.config;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public final class JwtRevocationFilter implements GlobalFilter, Ordered {

  private final WebClient webClient;
  private final String secret;
  private final java.util.concurrent.ConcurrentHashMap<String, Mono<Integer>> inFlight =
      new java.util.concurrent.ConcurrentHashMap<>();

  public JwtRevocationFilter(
      @LoadBalanced WebClient.Builder builder, @Value("${GATEWAY_INTERNAL_SECRET}") String secret) {
    this.webClient = builder.build();

    if (secret.isBlank()) {
      throw new IllegalArgumentException("GATEWAY_INTERNAL_SECRET must be set");
    }

    this.secret = secret;
  }

  @Override
  public int getOrder() {
    return -10;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    HttpMethod method = exchange.getRequest().getMethod();

    if (HttpMethod.POST.equals(method)
        && (path.equals("/api/users/login") || path.equals("/api/users/register"))) {
      return chain.filter(exchange);
    }

    return exchange
        .getPrincipal()
        .ofType(JwtAuthenticationToken.class)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .flatMap(
            principal -> {
              if (principal.isEmpty()) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
              }

              var jwt = principal.get().getToken();
              String role = jwt.getClaimAsString("role");

              if (!"USER".equals(role) && !"ADMIN".equals(role)) {
                return reject(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN");
              }

              return checkToken(jwt.getTokenValue(), exchange)
                  .flatMap(
                      status -> {
                        boolean changePassword =
                            HttpMethod.POST.equals(method)
                                && path.equals("/api/users/me/change-password");

                        if (status == 204 || (status == 403 && changePassword)) {
                          return chain.filter(exchange);
                        }

                        if (status == 401) {
                          return reject(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED");
                        }

                        if (status == 403) {
                          return reject(exchange, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED");
                        }

                        return reject(
                            exchange, HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVICE_UNAVAILABLE");
                      });
            });
  }

  /** Share in-flight revocation checks without caching the result after completion. */
  private Mono<Integer> checkToken(String token, ServerWebExchange exchange) {
    if (inFlight.size() > 2048) {
      return fetchTokenStatus(token, exchange);
    }
    return inFlight.computeIfAbsent(
        token,
        key -> {
          java.util.concurrent.atomic.AtomicReference<Mono<Integer>> self =
              new java.util.concurrent.atomic.AtomicReference<>();
          Mono<Integer> check =
              fetchTokenStatus(key, exchange)
                  .doFinally(signal -> inFlight.remove(key, self.get()))
                  .cache();
          self.set(check);
          return check;
        });
  }

  private Mono<Integer> fetchTokenStatus(String token, ServerWebExchange exchange) {
    return webClient
        .get()
        .uri("http://booking-service/api/users/token-status")
        .headers(
            headers -> {
              headers.setBearerAuth(token);
              headers.set(GatewaySecretFilter.HEADER, secret);
              String traceId = exchange.getRequest().getHeaders().getFirst(TraceWebFilter.HEADER);
              if (traceId != null) {
                headers.set(TraceWebFilter.HEADER, traceId);
              }
            })
        .exchangeToMono(response -> Mono.just(response.statusCode().value()))
        .timeout(Duration.ofSeconds(3))
        .onErrorReturn(503);
  }

  private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String code) {
    if (status == HttpStatus.UNAUTHORIZED) {
      exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }

    String message =
        switch (code) {
          case "INVALID_TOKEN" -> "Invalid or missing token";
          case "TOKEN_REVOKED" -> "Token has been revoked";
          case "PASSWORD_CHANGE_REQUIRED" -> "Password change required";
          case "AUTH_SERVICE_UNAVAILABLE" -> "Authentication service unavailable";
          case "FORBIDDEN" -> "Access denied";
          default -> "Request rejected";
        };
    return GatewayErrorResponseWriter.write(exchange, status, code, message);
  }
}
