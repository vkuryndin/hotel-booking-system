package com.hotelbooking.booking.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manually fills running H2 services through existing Gateway endpoints.
 *
 * <p>Click the green Run arrow next to {@link #main(String[])} in IntelliJ IDEA. No VM options,
 * Gradle tasks, extra passwords, or system properties are required. This class contains no JUnit
 * test methods, so unit tests and E2E All never invoke it. Start Eureka, Gateway, Booking Service,
 * and Hotel Service first. Data stays in H2 until services restart.
 */
class UiDataSeedManualTest extends ExtendedE2ESupport {

  private static final int HOTELS = 50_000;
  private static final int ROOMS = 5;
  private static final int USERS = 50_000;
  private static final int BOOKINGS = 50_000;
  private static final String PREFIX = "UISEED-";
  private static final String BOOKING_PASSWORD = "UiScaleTestPassword123!";
  private static final int BOOKING_USERS = 10;
  private static final int ROOMS_PER_HOTEL = ROOMS;
  private static final int PHOTO_EVERY_N_HOTELS = 20;
  private static final String[] CITIES = {
    "Yerevan",
    "Gyumri",
    "Dilijan",
    "Sevan",
    "Tsaghkadzor",
    "Jermuk",
    "Vanadzor",
    "Tbilisi",
    "Batumi",
    "Kutaisi",
    "Istanbul",
    "Antalya",
    "Izmir",
    "Athens",
    "Rome",
    "Milan",
    "Paris",
    "Nice",
    "Lyon",
    "Vienna",
    "Prague",
    "Berlin",
    "Hamburg",
    "Munich",
    "Lisbon",
    "Porto",
    "Madrid",
    "Barcelona",
    "Dubai",
    "Abu Dhabi",
    "Tokyo",
    "Kyoto",
    "Osaka",
    "London",
    "Edinburgh",
    "Warsaw",
    "Krakow",
    "Budapest",
    "Riga",
    "Tallinn",
    "Helsinki",
    "Stockholm",
    "Oslo",
    "Copenhagen",
    "Zurich",
    "Geneva",
    "Brussels",
    "Amsterdam"
  };
  private static final String[] LABELS = {
    "Garden", "Central", "Riverside", "Skyline", "Grand", "Boutique",
    "Park", "Mountain", "Lakeside", "Sunset", "Heritage", "City"
  };
  private static final String[] CATEGORIES = {"STANDARD", "DELUXE", "SUITE"};

  private static final class Session {
    private final String email;
    private final String password;
    private String token;
    private long renewAt;

    private Session(String email, String password, String token) {
      this.email = email;
      this.password = password;
      this.token = token;
      this.renewAt = System.currentTimeMillis() + Duration.ofMinutes(45).toMillis();
    }
  }

  public static void main(String[] args) throws Exception {
    new UiDataSeedManualTest().populate();
  }

  private void populate() throws Exception {
    long started = System.nanoTime();
    Account existingAdmin = admin();
    Session administrator =
        new Session(existingAdmin.email(), existingAdmin.password(), existingAdmin.token());
    assertServicesAvailable(administrator);
    assertNotPreviouslyStarted(administrator);
    byte[] photo = loadSamplePhoto();

    // The REST API creates bookings for the JWT owner, not an arbitrary userId.
    // Ten normal USER sessions let us create 50,000 bookings without 50,000 logins.
    List<Session> bookingUsers = new ArrayList<>(BOOKING_USERS);
    System.out.printf("[UI-SEED] Creating %,d users%n", USERS);
    for (int i = 0; i < USERS; i++) {
      String identity = String.format("%s%06d", PREFIX.toLowerCase(), i);
      String email = identity + "@example.test";
      Map<String, Object> user =
          Map.of(
              "email",
              email,
              "username",
              "uiseed" + String.format("%06d", i),
              "password",
              BOOKING_PASSWORD,
              "role",
              "USER",
              "firstName",
              "Test",
              "lastName",
              "Guest" + i);
      require(201, post("/api/users", administrator, user), "Create user #" + i);
      if (i < BOOKING_USERS) {
        bookingUsers.add(new Session(email, BOOKING_PASSWORD, ""));
      }
      progress("users", i + 1, USERS);
    }

    // Use one distinct room per hotel for the 50,000 bookings.
    List<Long> reservableRooms = new ArrayList<>(BOOKINGS);
    System.out.printf(
        "[UI-SEED] Creating %,d hotels and %,d rooms%n", HOTELS, HOTELS * ROOMS_PER_HOTEL);
    for (int i = 0; i < HOTELS; i++) {
      String city = CITIES[i % CITIES.length];
      String name =
          PREFIX + String.format("%06d", i) + " " + LABELS[i % LABELS.length] + " " + city;
      String address = city + ", Test Avenue " + (i + 1);
      Response hotel = post("/api/hotels", administrator, Map.of("name", name, "address", address));
      require(201, hotel, "Create hotel #" + i);
      long hotelId = requiredId(hotel, "hotel #" + i);
      // Use the existing presentation API so rating/amenity filters have data.
      Response presentation =
          json(
              "PATCH",
              "/api/hotels/" + hotelId + "/presentation",
              token(administrator),
              Map.of(
                  "description",
                      LABELS[i % LABELS.length]
                          + " stay in "
                          + city
                          + ", close to local attractions.",
                  "rating", 3.0 + (i % 21) / 10.0,
                  "amenities",
                      i % 3 == 0
                          ? List.of("WIFI", "PARKING", "RESTAURANT")
                          : List.of("WIFI", "GYM")));
      require(200, presentation, "Hotel presentation #" + i);

      for (int roomIndex = 0; roomIndex < ROOMS_PER_HOTEL; roomIndex++) {
        Map<String, Object> room = new LinkedHashMap<>();
        room.put("hotelId", hotelId);
        room.put("number", Integer.toString(101 + roomIndex));
        room.put("capacity", 1 + (roomIndex % 4));
        room.put("category", CATEGORIES[(i + roomIndex) % CATEGORIES.length]);
        room.put("pricePerNight", 60 + (i % 12) * 15 + roomIndex * 25);
        room.put(
            "amenities",
            roomIndex % 2 == 0 ? List.of("WIFI", "TV") : List.of("WIFI", "AIR_CONDITIONING"));
        Response createdRoom = post("/api/rooms", administrator, room);
        require(201, createdRoom, "Create room #" + roomIndex + " in hotel " + hotelId);
        if (roomIndex == 0) {
          reservableRooms.add(requiredId(createdRoom, "room"));
        }
      }
      if (i % PHOTO_EVERY_N_HOTELS == 0) {
        Response uploaded =
            upload(
                "/api/hotels/" + hotelId + "/photos",
                token(administrator),
                photo,
                "test-photo.jpg",
                "image/jpeg");
        require(201, uploaded, "Upload hotel photo #" + i);
      }
      progress("hotels", i + 1, HOTELS);
    }

    if (reservableRooms.size() < BOOKINGS) {
      throw new AssertionError(
          "Not enough rooms for required bookings: " + reservableRooms.size() + " < " + BOOKINGS);
    }

    System.out.printf("[UI-SEED] Creating %,d bookings through Saga%n", BOOKINGS);
    for (int i = 0; i < BOOKINGS; i++) {
      Session owner = bookingUsers.get(i % bookingUsers.size());
      LocalDate checkIn = LocalDate.now().plusDays(14 + (i % 120));
      LocalDate checkOut = checkIn.plusDays(1 + (i % 5));
      Map<String, Object> payload =
          Map.of(
              "roomId", reservableRooms.get(i),
              "autoSelect", false,
              "checkIn", checkIn.toString(),
              "checkOut", checkOut.toString(),
              "guestCount", 1);
      UUID idempotencyKey =
          UUID.nameUUIDFromBytes((PREFIX + "booking-" + i).getBytes(StandardCharsets.UTF_8));
      Response booking = postBooking(owner, payload, idempotencyKey);
      require(201, booking, "Create booking #" + i);
      long bookingId = requiredId(booking, "booking #" + i);
      if (i % 5 == 0) {
        Response canceled =
            send("PATCH", "/api/bookings/" + bookingId + "/cancel", token(owner), null, null);
        require(200, canceled, "Cancel booking #" + i);
      }
      progress("bookings", i + 1, BOOKINGS);
    }
    System.out.printf(
        "[UI-SEED] DONE hotels=%,d rooms=%,d users=%,d "
            + "bookings=%,d elapsed=%.1f min. Data remains until service restart.%n",
        HOTELS,
        HOTELS * ROOMS_PER_HOTEL,
        USERS,
        BOOKINGS,
        (System.nanoTime() - started) / 60_000_000_000.0);
  }

  private void assertServicesAvailable(Session administrator) {
    require(200, get("/api/hotels?size=1", token(administrator)), "Hotel Service readiness");
    require(200, get("/api/users?size=1", token(administrator)), "Booking Service readiness");
  }

  private void assertNotPreviouslyStarted(Session administrator) {
    // No silent duplicate population. For a clean run, restart both H2-backed services.
    Response response = get("/api/hotels?name=" + PREFIX + "&size=1", token(administrator));
    require(200, response, "Check existing seed data");
    if (node(response).path("totalElements").asLong(0) > 0) {
      throw new AssertionError(
          "UI seed data already exists. "
              + "Do not run twice on the same H2 database; restart services first.");
    }
  }

  private byte[] loadSamplePhoto() throws IOException {
    try (InputStream input = getClass().getResourceAsStream("/e2e-load/test-photo.jpg")) {
      if (input == null) {
        throw new IOException("Existing E2E photo not found: /e2e-load/test-photo.jpg");
      }
      return input.readAllBytes();
    }
  }

  private Response post(String route, Session session, Object value) {
    return json("POST", route, token(session), value);
  }

  private Response postBooking(Session session, Object payload, UUID idempotencyKey)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(BASE + "/api/bookings"))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + token(session))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Idempotency-Key", idempotencyKey.toString())
            .POST(HttpRequest.BodyPublishers.ofByteArray(om.writeValueAsBytes(payload)))
            .build();
    HttpResponse<byte[]> result = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    return new Response(result.statusCode(), result.body(), result.headers());
  }

  private String token(Session session) {
    if (System.currentTimeMillis() < session.renewAt && !session.token.isBlank()) {
      return session.token;
    }
    Response result =
        json(
            "POST",
            "/api/users/login",
            null,
            Map.of("email", session.email, "password", session.password));
    for (int attempt = 0; result.status() == 429 && attempt < 5; attempt++) {
      long seconds =
          result
              .headers()
              .firstValue("Retry-After")
              .map(
                  value -> {
                    try {
                      return Long.parseLong(value);
                    } catch (NumberFormatException ignored) {
                      return 60L;
                    }
                  })
              .orElse(60L);
      try {
        Thread.sleep(Math.max(1L, Math.min(65L, seconds + 1L)) * 1_000L);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted during login rate limit backoff", interrupted);
      }
      result =
          json(
              "POST",
              "/api/users/login",
              null,
              Map.of("email", session.email, "password", session.password));
    }
    require(200, result, "Login for seed account " + session.email);
    JsonNode response = node(result);
    session.token = response.path("accessToken").asText();
    if (session.token.isBlank()) {
      throw new AssertionError("Login response contained no JWT");
    }
    long ttl = response.path("expiresIn").asLong(3600);
    session.renewAt = System.currentTimeMillis() + Math.max(30, ttl - 120) * 1_000L;
    return session.token;
  }

  private long requiredId(Response response, String description) {
    long id = node(response).path("id").asLong(-1);
    if (id <= 0) {
      throw new AssertionError("Missing positive id in " + description + ": " + response.body());
    }
    return id;
  }

  private static void require(int status, Response response, String action) {
    if (response.status() != status) {
      String text = response.body();
      throw new AssertionError(
          action
              + " returned HTTP "
              + response.status()
              + ", expected "
              + status
              + ": "
              + text.substring(0, Math.min(text.length(), 600)));
    }
  }

  private static void progress(String stage, int done, int total) {
    if (done % 1_000 == 0 || done == total) {
      System.out.printf("[UI-SEED] %s: %,d/%,d%n", stage, done, total);
    }
  }
}
