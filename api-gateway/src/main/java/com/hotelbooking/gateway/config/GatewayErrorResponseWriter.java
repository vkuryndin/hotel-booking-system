package com.hotelbooking.gateway.config;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/** Writes the same public error fields as Booking and Hotel services. */
final class GatewayErrorResponseWriter {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private GatewayErrorResponseWriter() {}

  static Mono<Void> write(
      ServerWebExchange exchange, HttpStatus status, String code, String message) {
    var response = exchange.getResponse();
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", status.value());
    body.put("code", code);
    body.put("message", message);
    body.put("path", exchange.getRequest().getPath().value());

    String traceId = exchange.getRequest().getHeaders().getFirst(TraceWebFilter.HEADER);
    if (traceId != null && !traceId.isBlank()) {
      body.put("traceId", traceId);
    }

    byte[] bytes = JSON.writeValueAsBytes(body);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
  }
}
