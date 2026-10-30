package com.hotelbooking.booking.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Common real-HTTP harness, deliberately not a JUnit test class. No application mocks. */
abstract class SagaE2ESupport {
    static final String GATEWAY = "http://localhost:8080";
    static final String EUREKA = "http://localhost:8761";
    static final String PASS = "SagaE2ePassword123!";
    static final Duration CONVERGE = Duration.ofMinutes(6);
    final String run = UUID.randomUUID().toString().substring(0, 8);
    // Isolated future nights, not overlapping 11-test suite or earlier runs.
    final LocalDate start = LocalDate.now().plusDays(10000 + ThreadLocalRandom.current().nextInt(15000));
    final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(4)).build();
    final ObjectMapper om = new ObjectMapper();
    final Map<Long, String> ownedBookings = new ConcurrentHashMap<>();
    final List<Long> roomIds = new ArrayList<>();
    User owner;
    User admin;
    long hotelId;
    long roomId;

    record User(String email, String token) { }
    record R(int status, String body, long ms, String error) {
        boolean failedNetwork() { return status < 0; }
    }
    record Timed(R result, long ms) { }

    /** Optional local ADMIN credentials; never put passwords into Git or committed test sources. */
    private static Properties loadLocalAdminProperties() throws IOException {
        Properties properties = new Properties();
        Path[] candidates = {Path.of("secrets", "saga-e2e-admin.properties"),
                Path.of("booking-service", "secrets", "saga-e2e-admin.properties")};
        for (Path path : candidates) {
            if (Files.isRegularFile(path)) {
                try (InputStream input = Files.newInputStream(path)) {
                    properties.load(input);
                }
                break;
            }
        }
        return properties;
    }

    @BeforeAll
    void sagaSetup() throws Exception {
        // Gateway requires JWT for /api/hotels; never probe protected routes anonymously.
        // Registration and login are public and establish the authenticated probe identity.
        System.out.println("[SAGA HARNESS] Register -> login -> authenticated catalog precheck");
        owner = register("saga-owner");
        R health = get("/api/hotels?page=0&size=1", owner.token());
        assertEquals(200, health.status(),
                "Authenticated Gateway -> Hotel Service precheck failed: " + shortR(health));
        Properties adminSettings = loadLocalAdminProperties();
        String email = adminSettings.getProperty("email");
        String password = adminSettings.getProperty("password");
        if (email != null && password != null) {
            R login = post("/api/users/login", Map.of("email", email, "password", password), null, null);
            assertEquals(200, login.status(), "Invalid local Saga E2E ADMIN credentials: " + shortR(login));
            admin = new User(email, js(login).path("accessToken").asText());
            R hotel = post("/api/hotels", Map.of("name", "SAGA-E2E-" + run,
                    "address", "Isolated Saga E2E fixture " + run), admin.token(), null);
            assertEquals(201, hotel.status(), "Could not create test hotel: " + shortR(hotel));
            hotelId = js(hotel).path("id").asLong();
            for (int i = 1; i <= 4; i++) {
                R room = post("/api/rooms", Map.of("hotelId", hotelId,
                        "number", "SAGA-" + run + "-" + i, "capacity", 2,
                        "category", "STANDARD", "pricePerNight", 100), admin.token(), null);
                assertEquals(201, room.status(), "Could not create test room: " + shortR(room));
                roomIds.add(js(room).path("id").asLong());
            }
        } else {
            // Read-only discovery: no production hotel/room mutation other than far-future test bookings.
            R hotels = get("/api/hotels?page=0&size=100", owner.token());
            assertEquals(200, hotels.status(), shortR(hotels));
            for (JsonNode h : js(hotels).path("content")) {
                long hid = h.path("id").asLong();
                R rooms = get("/api/rooms?hotelId=" + hid + "&size=100", owner.token());
                if (rooms.status() != 200) continue;
                List<Long> candidates = new ArrayList<>();
                for (JsonNode r : js(rooms).path("content"))
                    if (r.path("available").asBoolean() && r.path("capacity").asInt() >= 1)
                        candidates.add(r.path("id").asLong());
                if (candidates.size() >= 2) {
                    hotelId = hid;
                    roomIds.addAll(candidates);
                    break;
                }
            }
        }
        assertTrue(hotelId > 0 && roomIds.size() >= 2,
                "Need an active hotel with >= 2 rooms or booking-service/secrets/saga-e2e-admin.properties");
        roomId = roomIds.get(0);
        System.out.printf("[SAGA SETUP] hotel=%d rooms=%d run=%s admin=%s%n",
                hotelId, roomIds.size(), run, admin != null);
    }

    @AfterAll
    void sagaCleanup() {
        // Never conceal the original test failure: best effort cancellation, but report unresolved resources.
        int cancelled = 0;
        int unresolved = 0;
        for (Map.Entry<Long, String> b : new ArrayList<>(ownedBookings.entrySet())) {
            try {
                R state = get("/api/bookings/" + b.getKey(), b.getValue());
                if (state.status() != 200) { unresolved++; continue; }
                String status = js(state).path("status").asText();
                if ("CONFIRMED".equals(status)) {
                    R response = patch("/api/bookings/" + b.getKey() + "/cancel", b.getValue());
                    if (response.status() == 200) cancelled++;
                    else unresolved++;
                } else if (!"CANCELLED".equals(status)) unresolved++;
            } catch (Exception ex) { unresolved++; }
        }
        System.out.printf("[SAGA CLEANUP] cancelled=%d unresolved=%d retainedHotel=%d%n",
                cancelled, unresolved, hotelId);
    }

    User register(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "saga-" + label + "-" + run + "-" + suffix + "@example.com";
        Map<String, Object> registration = Map.of(
                "email", email, "password", PASS,
                "username", "saga" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                "firstName", "Saga", "lastName", "E2E");
        assertEquals("Saga", registration.get("firstName"), "Missing firstName in Saga fixture");
        assertEquals("E2E", registration.get("lastName"), "Missing lastName in Saga fixture");
        R created = post("/api/users/register", registration, null, null);
        assertEquals(201, created.status(), "Registration failed: " + shortR(created));
        R login = post("/api/users/login", Map.of("email", email, "password", PASS), null, null);
        assertEquals(200, login.status(), "Login failed: " + shortR(login));
        String token = js(login).path("accessToken").asText();
        assertFalse(token.isBlank(), "Login did not return accessToken for " + email);
        return new User(email, token);
    }

    LocalDate day(int offset) { return start.plusDays(offset); }
    Map<String, Object> roomPayload(long id, LocalDate in, LocalDate out) {
        return Map.of("roomId", id, "checkIn", in.toString(), "checkOut", out.toString(), "guestCount", 1);
    }
    Map<String, Object> autoPayload(LocalDate in, LocalDate out) {
        return Map.of("hotelId", hotelId, "autoSelect", true, "checkIn", in.toString(),
                "checkOut", out.toString(), "guestCount", 1, "strategy", "LEAST_BOOKED");
    }
    R book(User u, Map<String, ?> body, UUID key) { return post("/api/bookings", body, u.token(), key); }
    R create(User u, long rid, LocalDate in, LocalDate out) {
        R r = book(u, roomPayload(rid, in, out), UUID.randomUUID());
        assertEquals(201, r.status(), "Booking creation failed: " + shortR(r));
        remember(r, u);
        return r;
    }
    long remember(R response, User u) {
        assertEquals(201, response.status(), "Must be 201: " + shortR(response));
        long id = js(response).path("id").asLong(-1);
        assertTrue(id > 0, "Missing booking ID: " + shortR(response));
        ownedBookings.put(id, u.token());
        return id;
    }
    R cancel(long id, User u) { return patch("/api/bookings/" + id + "/cancel", u.token()); }
    R booking(long id, User u) { return get("/api/bookings/" + id, u.token()); }
    long counter(long rid) {
        R res = get("/api/rooms/" + rid, owner.token());
        assertEquals(200, res.status(), "Room unavailable while checking timesBooked: " + shortR(res));
        return js(res).path("timesBooked").asLong(-1);
    }
    void expectStatus(long id, User u, String status, Duration timeout) {
        await("booking " + id + " status=" + status, timeout, () -> {
            R r = booking(id, u);
            return r.status() == 200 && status.equals(js(r).path("status").asText());
        });
    }
    void awaitCounter(long rid, long value, Duration timeout) {
        await("room " + rid + " timesBooked=" + value, timeout, () -> counter(rid) == value);
    }
    void await(String reason, Duration timeout, Supplier<Boolean> predicate) {
        long until = System.nanoTime() + timeout.toNanos();
        Throwable last = null;
        while (System.nanoTime() < until) {
            try { if (predicate.get()) return; }
            catch (Throwable t) { last = t; }
            pause(500);
        }
        fail("Timed out awaiting " + reason + (last == null ? "" : "; last=" + last));
    }
    void pause(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }
    JsonNode js(R result) { return js(result.body()); }
    JsonNode js(String data) {
        try { return om.readTree(data); }
        catch (Exception e) { throw new AssertionError("Bad JSON: " + data.substring(0, Math.min(200, data.length())), e); }
    }
    String errorCode(R r) { return r.status() > 0 ? js(r).path("code").asText() : "NETWORK_ERROR"; }
    static String shortR(R r) { return r.status() + " " + (r.error() != null ? r.error() : r.body().substring(0, Math.min(250, r.body().length()))); }
    static String env(String first, String second) {
        String a = System.getenv(first);
        if (a == null || a.isBlank()) a = System.getenv(second);
        return a == null || a.isBlank() ? null : a;
    }
    R get(String path, String token) { return send(GATEWAY, "GET", path, token, null, null, null); }
    R patch(String path, String token) { return send(GATEWAY, "PATCH", path, token, null, null, null); }
    R post(String path, Map<String, ?> data, String token, UUID key) {
        try { return send(GATEWAY, "POST", path, token, om.writeValueAsBytes(data), "application/json", key); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    R send(String base, String verb, String path, String token, byte[] body, String contentType, UUID key) {
        long t = System.nanoTime();
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(22))
                    .header("Accept", "application/json");
            if (token != null) b.header("Authorization", "Bearer " + token);
            if (contentType != null) b.header("Content-Type", contentType);
            if (key != null) b.header("Idempotency-Key", key.toString());
            b.method(verb, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
            HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new R(res.statusCode(), res.body(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-t), null);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new R(-1, "", TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-t), e.toString());
        }
    }
    List<Timed> parallel(String name, int total, int threads, IntFunction<R> f) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, total));
        AtomicInteger next = new AtomicInteger();
        Timed[] out = new Timed[total];
        List<Future<?>> pending = new ArrayList<>();
        CountDownLatch gate = new CountDownLatch(1);
        long began = System.nanoTime();
        try {
            for (int i = 0; i < Math.min(threads, total); i++) {
                pending.add(pool.submit(() -> {
                    try {
                        gate.await();
                        for (int j; (j = next.getAndIncrement()) < total;) {
                            long t = System.nanoTime();
                            R r = f.apply(j);
                            out[j] = new Timed(r, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-t));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }));
            }
            gate.countDown();
            for (Future<?> job : pending) job.get(14, TimeUnit.MINUTES);
        } finally { pool.shutdownNow(); }
        List<Timed> a = Arrays.asList(out);
        assertFalse(a.contains(null), "Incomplete load test " + name);
        List<Long> latency = a.stream().map(Timed::ms).sorted().toList();
        Map<Integer, Long> codes = new TreeMap<>();
        for (Timed x : a) codes.merge(x.result().status(), 1L, Long::sum);
        double seconds = (System.nanoTime()-began) / 1e9;
        System.out.printf(Locale.ROOT,
                "[SAGA %-24s] operations=%d workers=%d duration=%.2fs rps=%.1f p50=%dms p95=%dms p99=%dms codes=%s%n",
                name,total,Math.min(total,threads),seconds,total/Math.max(seconds,0.001),
                pct(latency,.5),pct(latency,.95),pct(latency,.99),codes);
        return a;
    }
    private static long pct(List<Long> sorted,double p){return sorted.get(Math.max(0,(int)Math.ceil(sorted.size()*p)-1));}
    void statusAll(List<Timed> results, int expected) {
        for (int i=0; i<results.size(); i++)
            assertEquals(expected,results.get(i).result().status(),"request " + i + ": " + shortR(results.get(i).result()));
    }
    void assertNoOverlaps(List<Long> ids, User u) {
        List<JsonNode> confirmed = new ArrayList<>();
        for (long id : ids) {
            R result = booking(id,u);
            assertEquals(200,result.status(),shortR(result));
            if ("CONFIRMED".equals(js(result).path("status").asText())) confirmed.add(js(result));
        }
        for (int i=0;i<confirmed.size();i++) for (int j=i+1;j<confirmed.size();j++) {
            JsonNode a=confirmed.get(i), b=confirmed.get(j);
            if (a.path("roomId").asLong() == b.path("roomId").asLong()) {
                LocalDate aIn=LocalDate.parse(a.path("checkIn").asText());
                LocalDate aOut=LocalDate.parse(a.path("checkOut").asText());
                LocalDate bIn=LocalDate.parse(b.path("checkIn").asText());
                LocalDate bOut=LocalDate.parse(b.path("checkOut").asText());
                assertFalse(aIn.isBefore(bOut) && bIn.isBefore(aOut),
                        "Double reservation for room=" + a.path("roomId").asLong());
            }
        }
    }

    // Eureka REST API: locate the exact instance ID and change ONLY that local instance.
    String eurekaInstanceId() {
        R res=send(EUREKA,"GET","/eureka/apps/HOTEL-SERVICE",null,null,null,null);
        assertEquals(200,res.status(),"Eureka registry unavailable: " + shortR(res));
        JsonNode instances=js(res).path("application").path("instance");
        assertTrue(instances.isArray() ? instances.size()==1 : !instances.isEmpty(),
                "Fault injection requires exactly one Hotel Service instance");
        JsonNode instance=instances.isArray()?instances.get(0):instances;
        assertEquals("UP",instance.path("status").asText(),
                "Refusing to change an instance that is not UP");
        String instanceId=instance.path("instanceId").asText();
        assertFalse(instanceId.isBlank(),"No Eureka instance ID");
        return instanceId;
    }
    String eurekaStatus(String id) {
        R res=send(EUREKA,"GET","/eureka/apps/HOTEL-SERVICE/"+escape(id),null,null,null,null);
        if (res.status()!=200) return "UNKNOWN";
        return js(res).path("instance").path("status").asText("UNKNOWN");
    }
    void setEurekaStatus(String id, String newStatus) {
        String path="/eureka/apps/HOTEL-SERVICE/"+escape(id)+"/status?value="+newStatus;
        R res=send(EUREKA,"PUT",path,null,null,null,null);
        assertTrue(res.status()==200||res.status()==204,"Eureka override failed: " + shortR(res));
        await("Eureka " + id + " status " + newStatus, Duration.ofSeconds(35),
                () -> newStatus.equals(eurekaStatus(id)));
        System.out.printf("[SAGA FAULT] HOTEL-SERVICE instance=%s status=%s%n",id,newStatus);
    }
    void restoreEureka(String id) {
        if (id==null) return;
        // A JUnit timeout may interrupt the test thread; clear it long enough
        // to perform the mandatory Eureka REST restoration, then propagate it.
        boolean interrupted=Thread.interrupted();
        try {
            setEurekaStatus(id,"UP");
            await("Hotel Gateway routing after restore", Duration.ofSeconds(65), () -> {
                R r=get("/api/rooms/" + roomId,owner.token());
                return r.status()==200;
            });
            System.out.println("[SAGA FAULT] Eureka status restored UP; routing healthy");
        } finally {
            if(interrupted) Thread.currentThread().interrupt();
        }
    }
    static String escape(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
}
