package com.hotelbooking.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/** Contract tests for responses produced by Gateway's own SecurityWebFilterChain. */
class GatewaySecurityErrorHandlerTest {

  private static final String TRACE_ID = "a0141b1f-563b-4aae-9bdb-bbd0aa9eaff2";
  private final GatewaySecurityErrorHandler handler = new GatewaySecurityErrorHandler();

  @Test
  void authenticationFailurePreservesBearerChallengeAndReturnsErrorJson() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/hotels")
                .header(TraceWebFilter.HEADER, TRACE_ID)
                .build());

    handler.commence(exchange, new BadCredentialsException("invalid token")).block();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    String challenge = exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE);
    assertNotNull(challenge);
    assertTrue(challenge.startsWith("Bearer"));
    assertJsonFields(exchange.getResponse().getBodyAsString().block(), 401, "UNAUTHORIZED");
  }

  @Test
  void accessDeniedReturnsErrorJsonWithoutChangingForbiddenStatus() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/hotels")
                .header(TraceWebFilter.HEADER, TRACE_ID)
                .build());

    handler.handle(exchange, new AccessDeniedException("denied")).block();

    assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    assertJsonFields(exchange.getResponse().getBodyAsString().block(), 403, "ACCESS_DENIED");
  }

  private static void assertJsonFields(String json, int status, String code) {
    assertNotNull(json);
    assertTrue(json.contains("\"timestamp\":"), json);
    assertTrue(json.contains("\"status\":" + status), json);
    assertTrue(json.contains("\"code\":\"" + code + "\""), json);
    assertTrue(json.contains("\"message\":"), json);
    assertTrue(json.contains("\"path\":\"/api/hotels\""), json);
    assertTrue(json.contains("\"traceId\":\"" + TRACE_ID + "\""), json);
  }
}
