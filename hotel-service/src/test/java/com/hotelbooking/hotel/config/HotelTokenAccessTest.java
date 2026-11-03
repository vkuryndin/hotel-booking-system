package com.hotelbooking.hotel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Hotel access: gateway-only users vs. Booking SERVICE token with exact subject/audience. */
class HotelTokenAccessTest {
  private UserTokenStateWebConfig interceptor;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setup() {
    interceptor = new UserTokenStateWebConfig("gateway-test-secret");
    request = new MockHttpServletRequest("GET", "/api/rooms");
    response = new MockHttpServletResponse();
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private void jwt(String subject, String role, String audience) {
    Jwt token =
        Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject(subject)
            .claim("role", role)
            .audience(List.of(audience))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
  }

  @Test
  void validServiceTokenIsAcceptedWithoutGatewayHeader() throws Exception {
    jwt("booking-service", "SERVICE", "hotel-service");
    assertTrue(interceptor.preHandle(request, response, new Object()));
  }

  @Test
  void serviceTokenWithWrongAudienceIsDenied() throws Exception {
    jwt("booking-service", "SERVICE", "booking-service");
    assertFalse(interceptor.preHandle(request, response, new Object()));
    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("INVALID_SERVICE_TOKEN"));
  }

  @Test
  void serviceTokenWithWrongSubjectIsDenied() throws Exception {
    jwt("other-service", "SERVICE", "hotel-service");
    assertFalse(interceptor.preHandle(request, response, new Object()));
    assertEquals(403, response.getStatus());
  }

  @Test
  void userJwtWithoutTrustedGatewayHeaderIsDenied() throws Exception {
    jwt("42", "USER", "hotel-service");
    assertFalse(interceptor.preHandle(request, response, new Object()));
    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("GATEWAY_REQUIRED"));
  }

  @Test
  void rejectedHotelRequestHasUniformErrorFieldsAndTraceId() throws Exception {
    jwt("42", "USER", "hotel-service");
    String traceId = "a0141b1f-563b-4aae-9bdb-bbd0aa9eaff2";
    MDC.put("traceId", traceId);
    try {
      assertFalse(interceptor.preHandle(request, response, new Object()));
      String body = response.getContentAsString();
      assertTrue(body.contains("\"timestamp\":"), body);
      assertTrue(body.contains("\"status\":403"), body);
      assertTrue(body.contains("\"code\":\"GATEWAY_REQUIRED\""), body);
      assertTrue(body.contains("\"message\":\"Use API Gateway\""), body);
      assertTrue(body.contains("\"path\":\"/api/rooms\""), body);
      assertTrue(body.contains("\"traceId\":\"" + traceId + "\""), body);
    } finally {
      MDC.remove("traceId");
    }
  }

  @Test
  void userJwtWithCorrectGatewayHeaderIsAccepted() throws Exception {
    jwt("42", "USER", "hotel-service");
    request.addHeader("X-Gateway-Internal-Secret", "gateway-test-secret");
    assertTrue(interceptor.preHandle(request, response, new Object()));
  }

  @Test
  void userJwtWithForgedGatewayHeaderIsDenied() throws Exception {
    jwt("42", "USER", "hotel-service");
    request.addHeader("X-Gateway-Internal-Secret", "wrong-secret");
    assertFalse(interceptor.preHandle(request, response, new Object()));
    assertEquals(403, response.getStatus());
  }
}
