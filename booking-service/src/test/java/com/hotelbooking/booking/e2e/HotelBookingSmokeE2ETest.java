package com.hotelbooking.booking.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Full user journey through the running Gateway, Booking, and Hotel services. All four applications
 * must be running before this test starts.
 */
@EnabledIfSystemProperty(named = "hotel.e2e", matches = "true")
class HotelBookingSmokeE2ETest {

  private static final String BASE_URL = "http://localhost:8080";
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void userCanRegisterLoginAndOpenCatalog() throws Exception {
    String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    String email = "e2e-" + unique + "@example.com";
    String password = "E2ePassword123!";

    HttpResponse<String> register =
        post(
            "/api/users/register",
            Map.of(
                "email",
                email,
                "password",
                password,
                "username",
                "e2e" + unique,
                "firstName",
                "E2E",
                "lastName",
                "Test"));
    assertEquals(201, register.statusCode(), register.body());

    HttpResponse<String> login =
        post(
            "/api/users/login",
            Map.of(
                "email", email,
                "password", password));
    assertEquals(200, login.statusCode(), login.body());
    String token = json.readTree(login.body()).path("accessToken").asText();
    assertFalse(token.isBlank(), "JWT was not returned");

    HttpResponse<String> myBookings = get("/api/bookings/my", token);
    assertEquals(200, myBookings.statusCode(), myBookings.body());
    assertTrue(json.readTree(myBookings.body()).path("content").isArray());

    HttpResponse<String> hotels = get("/api/hotels", token);
    assertEquals(200, hotels.statusCode(), hotels.body());
    JsonNode catalog = json.readTree(hotels.body());
    assertTrue(catalog.path("content").isArray(), "Expected a paginated hotel list");
  }

  private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(BASE_URL + path))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String path, String token) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(BASE_URL + path)).timeout(Duration.ofSeconds(15)).GET();
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
