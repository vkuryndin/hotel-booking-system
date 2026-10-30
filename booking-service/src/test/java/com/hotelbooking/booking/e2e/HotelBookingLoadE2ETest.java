package com.hotelbooking.booking.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * True HTTP E2E load / concurrency / consistency suite against the running Gateway (8080).
 * Included in ordinary Gradle clean test; deliberately no RUN_E2E flag or mocks.
 * See README-LOAD-E2E.md for fixture prerequisites and expected results.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = "hotel.e2e", matches = "true")
class HotelBookingLoadE2ETest {
    private static final String BASE = "http://localhost:8080";
    private static final int CONCURRENCY = 50;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration CONVERGENCE = Duration.ofSeconds(240);
    private static final String PASSWORD = "E2eLoadPassword123!";
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private final LocalDate epoch = LocalDate.now().plusDays(1500 + ThreadLocalRandom.current().nextInt(6000));
    private final List<Identity> users = new CopyOnWriteArrayList<>();
    private final Map<Long, ExpectedBooking> tracked = new ConcurrentHashMap<>();
    private final Map<Long, Long> initialTimesBooked = new LinkedHashMap<>();
    private final List<Long> hotelRoomIds = new ArrayList<>();
    private Identity primary;
    private Identity admin;
    private long hotelId;
    private long roomId;
    private String photoUrl;
    private Long createdHotel;
    private final List<Long> createdRoomIds = new ArrayList<>();
    private Long createdPhoto;

    private record Identity(String email, String token) { }
    private record ExpectedBooking(long id, String token, long roomId, LocalDate in,
                                   LocalDate out, String status) { }
    private record Result(int code, String body, long ms, String failure) {
        boolean timedOut() { return failure != null; }
    }
    private record Measured<T>(T value, long ms) { }

    @BeforeAll
    void setup() throws Exception {
        primary = registerAndLogin("owner");
        users.add(primary);
        // One additional identity for cross-user authorization checks; do not flood /register.
        users.add(registerAndLogin("secondary"));
        String adminEmail = config("E2E_ADMIN_EMAIL", "ADMIN_EMAIL");
        String adminPassword = config("E2E_ADMIN_PASSWORD", "ADMIN_PASSWORD");
        if (adminEmail != null && adminPassword != null) {
            Result res = post("/api/users/login", Map.of("email", adminEmail,
                    "password", adminPassword), null, null);
            assertEquals(200, res.code(), "ADMIN credentials rejected: " + snippet(res));
            admin = new Identity(adminEmail, field(res.body(), "accessToken"));
            // Prefer a dedicated isolated test hotel; no mutation to application source.
            setupDedicatedHotel();
        } else {
            discoverExistingHotel();
        }
        assertTrue(hotelId > 0 && roomId > 0, "E2E requires at least one hotel with an available room. "
                + "Create hotel + rooms through UI or configure E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD.");
        for (long id : hotelRoomIds) initialTimesBooked.put(id, room(id).path("timesBooked").asLong());
        System.out.printf("[E2E setup] hotel=%d rooms=%d mode=%s run=%s%n", hotelId,
                hotelRoomIds.size(), admin == null ? "existing fixture" : "dedicated ADMIN fixture", runId);
    }

    @AfterAll
    void cleanup() {
        long cleaned = 0;
        for (ExpectedBooking b : new ArrayList<>(tracked.values())) {
            try {
                Result state = get("/api/bookings/" + b.id(), b.token());
                if (state.code() == 200 && !"CANCELLED".equals(field(state.body(), "status"))) {
                    Result cancel = patch("/api/bookings/" + b.id() + "/cancel", b.token());
                    if (cancel.code() == 200) cleaned++;
                }
            } catch (Exception ignored) { /* retain original test failure; log only */ }
        }
        System.out.println("[E2E cleanup] cancellations=" + cleaned + " tracked=" + tracked.size());
        if (roomId > 0 && !tracked.isEmpty()) {
            // Final check AFTER cleanup, not just while the bookings are still CONFIRMED.
            awaitCondition("post-cleanup counters equal initial baseline", Duration.ofSeconds(90), () -> {
                for (long id : hotelRoomIds) {
                    if (room(id).path("timesBooked").asLong(-1) != initialTimesBooked.get(id))
                        return false;
                }
                return true;
            });
            System.out.println("[E2E cleanup] all room counters restored to baseline");
        }
        if (createdHotel != null) {
            System.out.println("[E2E cleanup] test hotel " + createdHotel
                    + " retained: historical room reservations prevent safe physical deletion");
        }
    }

    @Test @Order(1)
    void parallelAuthenticatedRequests100() throws Exception {
        // Both existing identities are reused; a public registration burst would exhaust
        // the production 5/hour/IP limit and invalidate unrelated E2E classes.
        List<Measured<Result>> answers = parallel("01-authenticated-reads", 100, 25,
                i -> getCompact("/api/hotels?page=0&size=20", user(i).token()));
        allStatus(answers, 200);
        assertEquals(100, answers.size());
    }

    @Test @Order(2)
    void catalogRead5000() throws Exception {
        Result sample = get("/api/hotels?page=0&size=20", primary.token());
        assertEquals(200, sample.code(), snippet(sample));
        assertTrue(json(sample.body()).path("content").isArray());
        List<Measured<Result>> results = parallel("02-catalog-GET", 5000, CONCURRENCY,
                i -> getCompact("/api/hotels?page=0&size=20", primary.token()));
        allStatus(results, 200);
    }

    @Test @Order(3)
    void competingForOneRoom200() throws Exception {
        LocalDate in = date(10);
        List<Measured<Result>> results = parallel("03-one-room-race", 200, CONCURRENCY, i ->
                book(user(i), roomRequest(roomId, in, in.plusDays(2)), UUID.randomUUID()));
        assertCodes(results, Set.of(201, 409));
        List<ExpectedBooking> confirmed = collectConfirmed(results, in, in.plusDays(2), roomId, this::user);
        assertEquals(1, confirmed.size(), "Only one HTTP 201 is permitted for the same room and nights");
        awaitStatus(confirmed.get(0), "CONFIRMED");
        assertEquals(1, countConfirmedAcrossUsers(in, in.plusDays(2), roomId));
    }

    @Test @Order(4)
    void sameIdempotencyKey200() throws Exception {
        LocalDate in = date(20);
        UUID key = UUID.randomUUID();
        Map<String, Object> request = roomRequest(roomId, in, in.plusDays(2));
        List<Measured<Result>> results = parallel("04-same-key-race", 200, CONCURRENCY,
                i -> book(primary, request, key));
        assertCodes(results, Set.of(201, 409));
        List<Long> ids = new ArrayList<>();
        for (Measured<Result> item : results) {
            if (item.value().code() == 201) {
                long id = json(item.value().body()).path("id").asLong(-1);
                assertTrue(id > 0, snippet(item.value()));
                ids.add(id);
            } else {
                assertEquals("REQUEST_IN_PROGRESS", errorCode(item.value()),
                        "409 for same key must be in-progress, not duplicate room reservation");
            }
        }
        assertFalse(ids.isEmpty(), "No successful request with the shared key");
        assertEquals(1, ids.stream().distinct().count(), "Multiple bookings for one Idempotency-Key");
        Result replay = awaitReplay(primary, request, key, Duration.ofSeconds(60));
        assertEquals(201, replay.code(), snippet(replay));
        assertEquals(ids.get(0).longValue(), json(replay.body()).path("id").asLong());
        ExpectedBooking b = remember(replay, primary, "CONFIRMED");
        awaitStatus(b, "CONFIRMED");
        assertEquals(1, countConfirmedAcrossUsers(in, in.plusDays(2), roomId));
    }

    @Test @Order(5)
    void parallelAutoSelect200() throws Exception {
        assertTrue(hotelRoomIds.size() >= 2, "AutoSelect distribution needs at least TWO rooms in the SAME hotel. "
                + "Create rooms through UI or supply E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD.");
        LocalDate in = date(30);
        List<Measured<Result>> results = parallel("05-autoSelect", 200, CONCURRENCY, i ->
                book(user(i), Map.of("hotelId", hotelId, "autoSelect", true,
                        "checkIn", in.toString(), "checkOut", in.plusDays(2).toString(),
                        "guestCount", 1, "strategy", "LEAST_BOOKED"), UUID.randomUUID()));
        assertCodes(results, Set.of(201, 409));
        List<ExpectedBooking> winners = collectConfirmed(results, in, in.plusDays(2), null, this::user);
        assertFalse(winners.isEmpty(), "AutoSelect failed to book any room");
        assertTrue(winners.size() <= hotelRoomIds.size(), "Too many confirmed bookings for inventory");
        assertEquals(winners.size(), winners.stream().map(ExpectedBooking::roomId).distinct().count(),
                "AutoSelect double-booked a room on the same dates");
        for (ExpectedBooking b : winners) awaitStatus(b, "CONFIRMED");
        assertEquals(winners.size(), countConfirmedAcrossUsers(in, in.plusDays(2), null));
    }

    @Test @Order(6)
    void parallelCancellation100() throws Exception {
        LocalDate in = date(40);
        long counterBefore = room(roomId).path("timesBooked").asLong();
        Result created = book(primary, roomRequest(roomId, in, in.plusDays(2)), UUID.randomUUID());
        assertEquals(201, created.code(), snippet(created));
        ExpectedBooking b = remember(created, primary, "CONFIRMED");
        awaitStatus(b, "CONFIRMED");
        List<Measured<Result>> results = parallel("06-cancel-same", 100, CONCURRENCY,
                i -> patch("/api/bookings/" + b.id() + "/cancel", primary.token()));
        assertCodes(results, Set.of(200, 409));
        awaitStatus(b, "CANCELLED");
        tracked.put(b.id(), new ExpectedBooking(b.id(), b.token(), b.roomId(), b.in(), b.out(), "CANCELLED"));
        awaitCondition("timesBooked decremented exactly once", Duration.ofSeconds(30), () ->
                room(roomId).path("timesBooked").asLong() == counterBefore);
        Result afterCancel = book(primary, roomRequest(roomId, in, in.plusDays(2)), UUID.randomUUID());
        assertEquals(201, afterCancel.code(), "Room not released after concurrent cancel: " + snippet(afterCancel));
        ExpectedBooking again = remember(afterCancel, primary, "CONFIRMED");
        awaitStatus(again, "CONFIRMED");
        Result cancelAgain = patch("/api/bookings/" + again.id() + "/cancel", primary.token());
        assertEquals(200, cancelAgain.code(), snippet(cancelAgain));
        awaitStatus(again, "CANCELLED");
        tracked.put(again.id(), new ExpectedBooking(again.id(), again.token(), again.roomId(),
                again.in(), again.out(), "CANCELLED"));
    }

    @Test @Order(7)
    void overlappingAndAdjacentDates200() throws Exception {
        LocalDate in = date(50);
        Result first = book(primary, roomRequest(roomId, in, in.plusDays(4)), UUID.randomUUID());
        assertEquals(201, first.code(), snippet(first));
        ExpectedBooking anchor = remember(first, primary, "CONFIRMED");
        List<Measured<Result>> results = parallel("07-overlap-adjacent", 200, CONCURRENCY, i ->
                book(user(i), roomRequest(roomId,
                        i < 100 ? in.plusDays(1) : in.plusDays(4),
                        i < 100 ? in.plusDays(3) : in.plusDays(6)), UUID.randomUUID()));
        assertCodes(results, Set.of(201, 409));
        assertEquals(0, results.subList(0, 100).stream().filter(x -> x.value().code() == 201).count(),
                "Overlapping nights must never be accepted");
        assertEquals(1, results.subList(100, 200).stream().filter(x -> x.value().code() == 201).count(),
                "Adjacent dates: one additional booking must succeed");
        collectConfirmed(results, in.plusDays(4), in.plusDays(6), roomId, this::user);
        assertEquals(1, countConfirmedAcrossUsers(in.plusDays(4), in.plusDays(6), roomId));
        awaitStatus(anchor, "CONFIRMED");
    }

    @Test @Order(8)
    void mixedTraffic2000() throws Exception {
        LocalDate seedDate = date(100);
        // Pre-create 100 reservations on non-overlapping nights for meaningful concurrent cancellation.
        List<Measured<Result>> seeded = parallel("08-seed", 100, 15, i ->
                book(primary, roomRequest(roomId, seedDate.plusDays(i * 3L),
                        seedDate.plusDays(i * 3L + 1)), UUID.randomUUID()));
        allStatus(seeded, 201);
        List<ExpectedBooking> toCancel = new ArrayList<>();
        for (Measured<Result> r : seeded) toCancel.add(remember(r.value(), primary, "CONFIRMED"));

        LocalDate fresh = date(1000);
        // 500 catalog GET + 500 own-bookings GET + 500 POST + 500 cancellation requests.
        List<Measured<Result>> results = parallel("08-mixed-2000", 2000, CONCURRENCY, i -> {
            int type = i % 4;
            int n = i / 4;
            return switch (type) {
                case 0 -> getCompact("/api/hotels?page=0&size=20", primary.token());
                case 1 -> getCompact("/api/bookings/my?page=0&size=20", primary.token());
                case 2 -> book(primary, roomRequest(roomId, fresh.plusDays(n * 2L),
                        fresh.plusDays(n * 2L + 1)), UUID.randomUUID());
                default -> patch("/api/bookings/" + toCancel.get(n % toCancel.size()).id()
                        + "/cancel", primary.token());
            };
        });
        for (int i = 0; i < results.size(); i++) {
            int type = i % 4;
            Result r = results.get(i).value();
            if (type == 0 || type == 1 || type == 2) assertEquals(type == 2 ? 201 : 200,
                    r.code(), "Mixed traffic operation=" + i + " " + snippet(r));
            else assertTrue(r.code() == 200 || r.code() == 409,
                    "Unexpected cancellation result op=" + i + " " + snippet(r));
            if (type == 2 && r.code() == 201) remember(r, primary, "CONFIRMED");
        }
        for (ExpectedBooking b : toCancel) {
            awaitStatus(b, "CANCELLED");
            tracked.put(b.id(), new ExpectedBooking(b.id(), b.token(), b.roomId(),
                    b.in(), b.out(), "CANCELLED"));
        }
    }

    @Test @Order(9)
    void parallelSecurity500() throws Exception {
        ExpectedBooking target = tracked.values().stream()
                .filter(b -> "CONFIRMED".equals(b.status()) && b.token().equals(primary.token()))
                .findFirst().orElseThrow(() -> new AssertionError("No booking fixture for access-control test"));
        List<Measured<Result>> results = parallel("09-security-500", 500, CONCURRENCY, i -> {
            int type = i % 4;
            return switch (type) {
                case 0 -> get("/api/bookings/my", null);
                case 1 -> get("/api/bookings/my", "not-a-jwt");
                case 2 -> get("/api/bookings/" + target.id(), otherUser(i).token());
                default -> get("/api/bookings/recovery/exhausted", otherUser(i).token());
            };
        });
        for (int i = 0; i < results.size(); i++) {
            assertEquals(i % 4 <= 1 ? 401 : 403, results.get(i).value().code(),
                    "Access-control error at request " + i + ": " + snippet(results.get(i).value()));
        }
        if (admin != null) {
            Result adminResponse = get("/api/bookings/recovery/exhausted", admin.token());
            assertEquals(200, adminResponse.code(), snippet(adminResponse));
        }
    }

    @Test @Order(10)
    void eventualSagaAndCountersConverge() throws Exception {
        assertFalse(tracked.isEmpty(), "No booking operations to verify");
        // Actual Booking Service state, not the initial POST status, is authoritative.
        for (ExpectedBooking b : tracked.values()) awaitStatus(b, b.status());
        // Check totalTimesBooked across every room in the fixture. Cancelled must not count.
        awaitCondition("hotel room timesBooked consistency", CONVERGENCE, () -> {
            for (long id : hotelRoomIds) {
                long expected = initialTimesBooked.get(id)
                        + tracked.values().stream().filter(b -> b.roomId() == id)
                        .filter(b -> "CONFIRMED".equals(b.status())).count();
                long actual = room(id).path("timesBooked").asLong(-1);
                if (actual != expected) return false;
            }
            return true;
        });
        // Cross-service view: a booked room must be absent from date-sensitive recommendations.
        List<ExpectedBooking> confirmed = tracked.values().stream()
                .filter(b -> "CONFIRMED".equals(b.status())).limit(20).toList();
        for (ExpectedBooking b : confirmed) {
            String url = "/api/rooms/recommend?hotelId=" + hotelId
                    + "&checkIn=" + b.in() + "&checkOut=" + b.out() + "&guestCount=1";
            Result query = get(url, primary.token());
            assertEquals(200, query.code(), snippet(query));
            for (JsonNode candidate : json(query.body())) {
                assertNotEquals(b.roomId(), candidate.path("id").asLong(),
                        "Hotel Service recommends an already reserved room " + b.roomId());
            }
        }
        if (admin != null) {
            Result stats = get("/api/hotels/" + hotelId + "/statistics", admin.token());
            assertEquals(200, stats.code(), snippet(stats));
            long sum = 0;
            for (long id : hotelRoomIds) sum += room(id).path("timesBooked").asLong();
            assertEquals(sum, json(stats.body()).path("totalTimesBooked").asLong(),
                    "Hotel statistics differ from individual room counters");
        }
        System.out.println("[E2E consistency] verified bookings=" + tracked.size()
                + " rooms=" + hotelRoomIds.size() + " date-window checks=" + confirmed.size());
    }

    @Test @Order(11)
    void parallelPhotoReads1000VerifySha256() throws Exception {
        assertNotNull(photoUrl, "No hotel photo exists. Upload a photo using UI, "
                + "or configure E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD to generate a fixture.");
        byte[] reference = download(photoUrl, primary.token());
        assertTrue(reference.length > 20, "Photo fixture is empty");
        assertTrue(reference.length <= 1_000_000, "Photo fixture exceeds 1MB; 1000 GETs would transfer "
                + (reference.length / 1_000_000) + "GB. Use a smaller image or ADMIN fixture.");
        String digest = sha256(reference);
        List<Measured<String>> results = parallel("11-photo-1000-SHA256", 1000, CONCURRENCY,
                i -> {
                    try { return sha256(download(photoUrl, primary.token())); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
        for (Measured<String> r : results) assertEquals(digest, r.value(), "Image content mismatch");
    }

    // ---------- Real HTTP + test data ----------

    private Identity registerAndLogin(String name) throws Exception {
        String email = "e2e-" + name + "-" + runId + "-" + UUID.randomUUID().toString().substring(0, 6)
                + "@example.com";
        Result reg = post("/api/users/register", Map.of("email", email, "password", PASSWORD,
                "username", "e2e" + UUID.randomUUID().toString().replace("-", "").substring(0, 14),
                "firstName", "Load", "lastName", "E2E"), null, null);
        if (reg.timedOut()) {
            // Registration may have committed: do not send a second non-idempotent registration.
            awaitCondition("registration completed for " + email, Duration.ofSeconds(90), () ->
                    post("/api/users/login", Map.of("email", email, "password", PASSWORD), null, null).code() == 200);
        } else assertEquals(201, reg.code(), "Register " + email + ": " + snippet(reg));
        Result login = post("/api/users/login", Map.of("email", email, "password", PASSWORD), null, null);
        assertEquals(200, login.code(), "Login " + email + ": " + snippet(login));
        String token = field(login.body(), "accessToken");
        assertFalse(token.isBlank());
        return new Identity(email, token);
    }

    private void setupDedicatedHotel() throws Exception {
        Result h = post("/api/hotels", Map.of("name", "E2E LOAD " + runId,
                "address", "E2E isolated " + runId), admin.token(), null);
        assertEquals(201, h.code(), snippet(h));
        createdHotel = json(h.body()).path("id").asLong();
        hotelId = createdHotel;
        for (int i = 1; i <= 10; i++) {
            Result r = post("/api/rooms", Map.of("hotelId", hotelId,
                    "number", "LOAD-" + i, "capacity", 2,
                    "category", "STANDARD", "pricePerNight", 100), admin.token(), null);
            assertEquals(201, r.code(), snippet(r));
            long id = json(r.body()).path("id").asLong();
            createdRoomIds.add(id);
            hotelRoomIds.add(id);
        }
        roomId = hotelRoomIds.get(0);
        try (InputStream source = getClass().getResourceAsStream("/e2e-load/test-photo.jpg")) {
            assertNotNull(source, "Missing test-photo.jpg fixture");
            byte[] photo = source.readAllBytes();
            String boundary = "E2E" + UUID.randomUUID().toString().replace("-", "");
            byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"e2e.jpg\"\r\n"
                    + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] body = new byte[prefix.length + photo.length + suffix.length];
            System.arraycopy(prefix, 0, body, 0, prefix.length);
            System.arraycopy(photo, 0, body, prefix.length, photo.length);
            System.arraycopy(suffix, 0, body, prefix.length + photo.length, suffix.length);
            Result uploaded = send("POST", "/api/hotels/" + hotelId + "/photos",
                    admin.token(), body, "multipart/form-data; boundary=" + boundary);
            assertEquals(201, uploaded.code(), "Photo fixture upload: " + snippet(uploaded));
            createdPhoto = json(uploaded.body()).path("id").asLong();
            photoUrl = json(uploaded.body()).path("url").asText();
        }
    }

    private void discoverExistingHotel() throws Exception {
        Result hotels = get("/api/hotels?page=0&size=100", primary.token());
        assertEquals(200, hotels.code(), snippet(hotels));
        int bestCount = 0;
        for (JsonNode h : json(hotels.body()).path("content")) {
            long id = h.path("id").asLong();
            Result found = get("/api/rooms?hotelId=" + id + "&page=0&size=100", primary.token());
            if (found.code() != 200) continue;
            List<Long> available = new ArrayList<>();
            for (JsonNode r : json(found.body()).path("content")) {
                if (r.path("available").asBoolean() && r.path("capacity").asInt(1) >= 1)
                    available.add(r.path("id").asLong());
            }
            if (available.size() > bestCount) {
                hotelId = id;
                hotelRoomIds.clear();
                hotelRoomIds.addAll(available);
                roomId = available.get(0);
                bestCount = available.size();
            }
            if (bestCount >= 2) break;
        }
        if (hotelId > 0) {
            Result photos = get("/api/hotels/" + hotelId + "/photos", primary.token());
            if (photos.code() == 200 && json(photos.body()).isArray()
                    && !json(photos.body()).isEmpty()) {
                photoUrl = json(photos.body()).get(0).path("url").asText();
            } else {
                // A photo may exist on another hotel, but will not influence the booking fixture.
                for (JsonNode h : json(hotels.body()).path("content")) {
                    Result p = get("/api/hotels/" + h.path("id").asLong() + "/photos", primary.token());
                    if (p.code() == 200 && !json(p.body()).isEmpty()) {
                        photoUrl = json(p.body()).get(0).path("url").asText(); break;
                    }
                }
            }
        }
    }

    private Identity user(int index) { return users.get(Math.floorMod(index, users.size())); }
    private Identity otherUser(int index) {
        assertTrue(users.size() >= 2, "Registration test must have created another user");
        return users.get(1 + Math.floorMod(index, users.size() - 1));
    }
    private LocalDate date(long offset) { return epoch.plusDays(offset); }
    private Map<String, Object> roomRequest(long id, LocalDate in, LocalDate out) {
        return Map.of("roomId", id, "autoSelect", false, "checkIn", in.toString(),
                "checkOut", out.toString(), "guestCount", 1);
    }
    private Result book(Identity user, Map<String, Object> request, UUID key) {
        Result first = post("/api/bookings", request, user.token(), key);
        if (!first.timedOut()) return first;
        // A network timeout does NOT mean that the server rolled back.
        // Resolve the outcome by replaying the exact same Idempotency-Key, never a new key.
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        Result latest = first;
        while (System.nanoTime() < deadline) {
            pause(350);
            latest = post("/api/bookings", request, user.token(), key);
            if (latest.timedOut()) continue;
            if (latest.code() == 409 && "REQUEST_IN_PROGRESS".equals(errorCode(latest))) continue;
            System.out.println("[E2E delay] booking timeout reconciled by replay key=" + key
                    + " -> " + latest.code());
            return new Result(latest.code(), latest.body(), first.ms() + latest.ms(),
                    "timeout reconciled by exact Idempotency-Key replay");
        }
        return first; // explicit failure when no definite final answer
    }
    private JsonNode room(long id) {
        Result r = get("/api/rooms/" + id, primary.token());
        assertEquals(200, r.code(), snippet(r));
        return json(r.body());
    }

    private ExpectedBooking remember(Result response, Identity owner, String expectedStatus) {
        JsonNode v = json(response.body());
        long id = v.path("id").asLong(-1);
        assertTrue(id > 0, snippet(response));
        ExpectedBooking b = new ExpectedBooking(id, owner.token(), v.path("roomId").asLong(),
                LocalDate.parse(v.path("checkIn").asText()), LocalDate.parse(v.path("checkOut").asText()),
                expectedStatus);
        tracked.put(id, b);
        return b;
    }

    private List<ExpectedBooking> collectConfirmed(List<Measured<Result>> responses,
                                                    LocalDate in, LocalDate out, Long requestedRoom,
                                                    IntFunction<Identity> requestOwner) {
        List<ExpectedBooking> all = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            Result r = responses.get(i).value();
            if (r.code() == 201) {
                JsonNode data = json(r.body());
                assertEquals(in.toString(), data.path("checkIn").asText());
                assertEquals(out.toString(), data.path("checkOut").asText());
                if (requestedRoom != null) assertEquals(requestedRoom.longValue(), data.path("roomId").asLong());
                all.add(remember(r, requestOwner.apply(i), "CONFIRMED"));
            }
        }
        return all;
    }

    private long countConfirmedAcrossUsers(LocalDate in, LocalDate out, Long roomFilter) {
        long count = 0;
        for (Identity user : users) {
            int page = 0;
            while (true) {
                Result r = get("/api/bookings/my?page=" + page + "&size=100", user.token());
                assertEquals(200, r.code(), snippet(r));
                JsonNode list = json(r.body()).path("content");
                assertTrue(list.isArray(), "Booking list is not a paginated JSON array");
                for (JsonNode b : list) {
                    if ("CONFIRMED".equals(b.path("status").asText())
                            && in.toString().equals(b.path("checkIn").asText())
                            && out.toString().equals(b.path("checkOut").asText())
                            && (roomFilter == null || roomFilter == b.path("roomId").asLong())) count++;
                }
                if (list.size() == 0 || page + 1 >= json(r.body()).path("totalPages").asInt(1)) break;
                page++;
                assertTrue(page < 100, "Excessive booking pages; fixture is not isolated");
            }
        }
        return count;
    }

    private void awaitStatus(ExpectedBooking b, String expected) {
        awaitCondition("booking=" + b.id() + " status=" + expected, CONVERGENCE, () -> {
            Result r = get("/api/bookings/" + b.id(), b.token());
            return r.code() == 200 && expected.equals(field(r.body(), "status"));
        });
    }

    private Result awaitReplay(Identity owner, Map<String, Object> payload, UUID key, Duration limit) {
        long until = System.nanoTime() + limit.toNanos();
        Result latest = null;
        while (System.nanoTime() < until) {
            latest = book(owner, payload, key);
            if (latest.code() == 201) return latest;
            assertTrue(latest.code() == 409 && "REQUEST_IN_PROGRESS".equals(errorCode(latest)),
                    "Unexpected replay response: " + snippet(latest));
            pause(200);
        }
        fail("Same-key replay never settled: " + snippet(latest));
        return latest;
    }

    private void awaitCondition(String name, Duration timeout, Callable<Boolean> predicate) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Throwable last = null;
        while (System.nanoTime() < deadline) {
            try { if (predicate.call()) return; }
            catch (Exception e) { last = e; }
            pause(250); // polling, NOT fixed waiting instead of verifying the eventual state
        }
        fail("No eventual convergence for " + name + (last == null ? "" : ": " + last));
    }

    private <T> List<Measured<T>> parallel(String name, int total, int workers,
                                          IntFunction<T> action) throws Exception {
        int threads = Math.min(workers, total);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        Object[] values = new Object[total];
        AtomicInteger next = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> running = new ArrayList<>();
        long started = System.nanoTime();
        try {
            for (int t = 0; t < threads; t++) {
                running.add(executor.submit(() -> {
                    try {
                        start.await();
                        for (int i; (i = next.getAndIncrement()) < total;) {
                            long begin = System.nanoTime();
                            T res = action.apply(i);
                            values[i] = new Measured<>(res, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }));
            }
            start.countDown();
            for (Future<?> f : running) f.get(15, TimeUnit.MINUTES);
        } finally {
            executor.shutdownNow();
        }
        List<Measured<T>> result = new ArrayList<>();
        for (Object o : values) {
            assertNotNull(o, "One concurrent operation did not complete: " + name);
            @SuppressWarnings("unchecked") Measured<T> m = (Measured<T>) o;
            result.add(m);
        }
        double seconds = (System.nanoTime() - started) / 1e9;
        List<Long> latency = result.stream().map(Measured::ms).sorted().toList();
        long failures = result.stream().filter(x -> x.value() instanceof Result r && r.timedOut()).count();
        Map<Integer, Long> codes = new TreeMap<>();
        for (Measured<T> m : result) {
            if (m.value() instanceof Result r) codes.merge(r.code(), 1L, Long::sum);
        }
        System.out.printf(Locale.ROOT, "[E2E %-25s] count=%d concurrency=%d duration=%.2fs rps=%.1f "
                        + "p50=%dms p95=%dms p99=%dms networkTimeouts=%d statuses=%s%n",
                name, total, threads, seconds, total / Math.max(seconds, 0.001),
                percentile(latency, 0.50), percentile(latency, 0.95), percentile(latency, 0.99), failures, codes);
        return result;
    }

    private static long percentile(List<Long> sorted, double pct) {
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * pct) - 1));
    }

    private void allStatus(List<Measured<Result>> results, int code) {
        for (int i = 0; i < results.size(); i++)
            assertEquals(code, results.get(i).value().code(), "request " + i + ": " + snippet(results.get(i).value()));
    }
    private void assertCodes(List<Measured<Result>> results, Set<Integer> codes) {
        for (int i = 0; i < results.size(); i++)
            assertTrue(codes.contains(results.get(i).value().code()),
                    "Unexpected HTTP status request " + i + ": " + snippet(results.get(i).value()));
    }

    // Discard large JSON catalog bodies in 5000/2000-op batches to keep test JVM memory bounded.
    private Result getCompact(String path, String token) {
        Result r = get(path, token);
        return new Result(r.code(), "", r.ms(), r.failure());
    }
    private Result get(String path, String token) {
        Result first = send("GET", path, token, null, null);
        if (!first.timedOut()) return first;
        Result second = send("GET", path, token, null, null);
        return second.timedOut() ? first : new Result(second.code(), second.body(),
                first.ms() + second.ms(), "GET succeeded after network timeout retry");
    }
    private Result patch(String path, String token) {
        Result first = send("PATCH", path, token, null, null);
        if (!first.timedOut()) return first;
        // Cancellation is idempotent: observe final state before retrying.
        String lookup = path.endsWith("/cancel") ? path.substring(0, path.length() - 7) : path;
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < deadline) {
            Result current = get(lookup, token);
            if (current.code() == 200 && "CANCELLED".equals(field(current.body(), "status")))
                return new Result(200, current.body(), first.ms() + current.ms(),
                        "PATCH timeout reconciled from server state");
            pause(350);
            Result retry = send("PATCH", path, token, null, null);
            if (retry.code() == 200) return new Result(200, retry.body(),
                    first.ms() + retry.ms(), "PATCH timeout reconciled by idempotent retry");
            if (retry.code() != 409 && !retry.timedOut()) return retry;
        }
        return first;
    }
    private Result post(String path, Map<String, ?> payload, String token, UUID key) {
        try { return send("POST", path, token, mapper.writeValueAsBytes(payload), "application/json", key); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }
    private Result send(String method, String path, String token, byte[] body, String contentType) {
        return send(method, path, token, body, contentType, null);
    }
    private Result send(String method, String path, String token, byte[] body, String contentType, UUID key) {
        long start = System.nanoTime();
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(REQUEST_TIMEOUT);
            if (token != null) b.header("Authorization", "Bearer " + token);
            if (contentType != null) b.header("Content-Type", contentType);
            if (key != null) b.header("Idempotency-Key", key.toString());
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(body));
            HttpResponse<String> response = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Result(response.statusCode(), response.body(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), e.toString());
        } catch (Exception e) {
            return new Result(-1, "", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), e.toString());
        }
    }

    private byte[] download(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(REQUEST_TIMEOUT).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<byte[]> response = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode(), "Photo GET " + path);
        return response.body();
    }

    private static String sha256(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }
    private JsonNode json(String s) {
        try { return mapper.readTree(s); }
        catch (Exception ex) { throw new AssertionError("Invalid JSON: " + (s == null ? "null" : s.substring(0, Math.min(s.length(), 150))), ex); }
    }
    private String field(String s, String key) { return json(s).path(key).asText(); }
    private String errorCode(Result r) {
        if (r.code() < 0) return "NETWORK_ERROR";
        try { return json(r.body()).path("code").asText(); }
        catch (Throwable t) { return ""; }
    }
    private static String snippet(Result r) {
        if (r == null) return "no response";
        return "HTTP " + r.code() + " " + (r.failure() != null ? r.failure()
                : r.body().substring(0, Math.min(260, r.body().length())));
    }
    private static String config(String primary, String fallback) {
        String v = System.getenv(primary);
        if (v == null || v.isBlank()) v = System.getenv(fallback);
        return v == null || v.isBlank() ? null : v;
    }
    private static void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }
}
