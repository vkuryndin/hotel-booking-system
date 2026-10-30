package com.hotelbooking.booking.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly opt-in live-HTTP E2E support. No mocked applications. */
abstract class ExtendedE2ESupport {
    static final String BASE = System.getProperty("hotel.e2e.base", "http://localhost:8080");
    static final String PASSWORD = "E2eExtendedPassword123!";
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final ObjectMapper om = new ObjectMapper();
    final String runId = UUID.randomUUID().toString().substring(0, 8);
    record Response(int status, byte[] bytes, HttpHeaders headers) {
        String body() { return new String(bytes, java.nio.charset.StandardCharsets.UTF_8); }
    }
    record Account(String email, String password, String token) {}
    private static volatile Account cachedAdmin;

    Response send(String method, String route, String jwt, byte[] bytes, String mime) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + route))
                    .timeout(Duration.ofSeconds(40)).header("Accept", "application/json");
            if (jwt != null) b.header("Authorization", "Bearer " + jwt);
            if (mime != null) b.header("Content-Type", mime);
            b.method(method, bytes == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(bytes));
            HttpResponse<byte[]> response = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Response(response.statusCode(), response.body(), response.headers());
        } catch (Exception ex) { throw new AssertionError(method + " " + route + " network failure", ex); }
    }
    Response json(String method, String path, String jwt, Object value) {
        try { return send(method, path, jwt, om.writeValueAsBytes(value), "application/json"); }
        catch (IOException e) { throw new AssertionError(e); }
    }
    Response get(String path, String jwt) { return send("GET",path,jwt,null,null); }
    Response delete(String path, String jwt) { return send("DELETE",path,jwt,null,null); }
    JsonNode node(Response r) {
        try { return om.readTree(r.bytes()); }
        catch (IOException e) { throw new AssertionError("Not JSON: HTTP="+r.status()+" "+r.body(),e); }
    }
    void expect(int expected, Response r) { assertEquals(expected,r.status(),r.body()); }
    Account register(String prefix) {
        String email = prefix + "." + runId + "." + UUID.randomUUID().toString().substring(0, 7) + "@example.test";
        String username = ("ext" + UUID.randomUUID().toString().replace("-", "")).substring(0, 20);
        // Test fixture creation uses the existing ADMIN API, not the public
        // registration endpoint (5 requests/hour/IP in production).
        Response reg = json("POST","/api/users",admin().token(),Map.of("email",email,"password",PASSWORD,
                "role","USER", "username",username,"firstName","E2E","lastName","Test"));
        expect(201,reg);
        return new Account(email,PASSWORD, login(email,PASSWORD));
    }
    String login(String email,String password) {
        Response r = json("POST","/api/users/login",null,Map.of("email",email,"password",password));
        expect(200,r);
        String token = node(r).path("accessToken").asText();
        assertFalse(token.isBlank()); return token;
    }
    long createHotel(String token) {
        Response r = json("POST","/api/hotels",token,Map.of("name","E2E-EXT-"+runId+"-"+UUID.randomUUID().toString().substring(0,5),"address","Test-only fixture"));
        expect(201,r); return node(r).path("id").asLong();
    }
    long createRoom(String token,long hotelId) {
        Response r = json("POST","/api/rooms",token,Map.of("hotelId",hotelId,
                "number","EX-"+UUID.randomUUID().toString().substring(0,8), "capacity",2,
                "category","STANDARD", "pricePerNight",100));
        expect(201,r);return node(r).path("id").asLong();
    }
    Response upload(String prefix,String token,byte[] content,String filename,String mime) {
        String boundary = "----e2e" + UUID.randomUUID().toString().replace("-", "");
        byte[] a = ("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+
                "\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] z = ("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] all = new byte[a.length+content.length+z.length];
        System.arraycopy(a,0,all,0,a.length);System.arraycopy(content,0,all,a.length,content.length);
        System.arraycopy(z,0,all,a.length+content.length,z.length);
        return send("POST",prefix,token,all,"multipart/form-data; boundary="+boundary);
    }
    static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAAAFElEQVR4nGPUylvAAANMDEgANwcAPX4BQJVh6wIAAAAASUVORK5CYII=");
    /** Read the existing demo admin fixture from requests.http; no credentials copied into test sources. */
    Account admin() {
        Account ready = cachedAdmin;
        if (ready != null) return ready;
        synchronized (ExtendedE2ESupport.class) {
            if (cachedAdmin != null) return cachedAdmin;
            try {
                Path requests = Files.isRegularFile(Path.of("requests.http")) ? Path.of("requests.http") : Path.of("..", "requests.http");
                String txt = Files.readString(requests);
                java.util.regex.Matcher section = java.util.regex.Pattern.compile(
                        "(?s)### Login as ADMIN\\s*POST[^\\r\\n]*\\R[^\\r\\n]*\\R\\s*(\\{.*?\\})").matcher(txt);
                if (!section.find()) throw new AssertionError("Admin login block not found in requests.http");
                JsonNode creds = om.readTree(section.group(1));
                String email = creds.path("email").asText();
                String password = creds.path("password").asText();
                if (email.isBlank() || password.isBlank()) throw new AssertionError("Empty demo admin credentials");
                cachedAdmin = new Account(email, password, login(email, password));
                return cachedAdmin;
            } catch (IOException ex) { throw new AssertionError("Cannot load requests.http demo admin account", ex); }
        }
    }
    /** Auto-provision and cache exactly 200 USER accounts; no external TSV or manual preparation. */
    synchronized List<Account> suppliedUsers() throws IOException {
        Path file = Path.of("build", "e2e-fixtures", "two-hundred-users.tsv");
        if (!Files.isRegularFile(file)) {
            Account administrator = admin();
            Files.createDirectories(file.getParent());
            String prefix = "e2e200" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                String email = prefix + "." + i + "@example.test";
                String username = prefix + "u" + i;
                Response created = json("POST", "/api/users", administrator.token(), Map.of(
                    "email", email, "password", PASSWORD, "role", "USER", "username", username,
                    "firstName", "Load", "lastName", "Fixture"));
                expect(201, created);
                lines.add(email + "\t" + PASSWORD);
            }
            Files.write(file, lines, java.nio.charset.StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        List<Account> accounts = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split("\t", -1);
            assertEquals(2, parts.length, "Expected email TAB password");
            accounts.add(new Account(parts[0], parts[1], null));
        }
        assertEquals(200, accounts.size());
        assertEquals(200, accounts.stream().map(Account::email).distinct().count());
        return accounts;
    }
    static <T> List<T> concurrent(int workers, List<Callable<T>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            List<Future<T>> results = new ArrayList<>();
            for (Callable<T> job : jobs) results.add(pool.submit(() -> { gate.await(); return job.call(); }));
            gate.countDown();
            List<T> out = new ArrayList<>();
            for (Future<T> f : results) out.add(f.get(2,TimeUnit.MINUTES));
            return out;
        } finally {pool.shutdownNow();}
    }
}
