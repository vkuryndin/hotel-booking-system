package com.hotelbooking.booking.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
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
    private static volatile Account sharedUser;

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

    /**
     * Opens a genuine TCP connection from a distinct loopback IP. The Gateway obtains
     * the client IP from the socket, never from a client-supplied forwarding header.
     * This keeps production IP rate limits intact and prevents a 200-login test from
     * exhausting the shared 127.0.0.1 limit used by ordinary E2E tests.
     * Requires local IPv4 loopback routing (Windows/Linux/macOS).
     */
    Response jsonFromLoopback(String path, Object value, int addressNumber) {
        try {
            URI uri = URI.create(BASE + path);
            if (!"http".equalsIgnoreCase(uri.getScheme()))
                throw new AssertionError("Loopback stress test requires HTTP Gateway URL: " + uri);
            String ip = "127.77." + (1 + addressNumber / 250) + "." + (1 + addressNumber % 250);
            byte[] payload = om.writeValueAsBytes(value);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 80 : uri.getPort();
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.setSoTimeout(45_000);
                socket.bind(new java.net.InetSocketAddress(java.net.InetAddress.getByName(ip), 0));
                socket.connect(new java.net.InetSocketAddress(host, port), 8_000);
                String header = "POST " + uri.getRawPath() + " HTTP/1.1\r\n" +
                        "Host: " + host + ":" + port + "\r\n" +
                        "Content-Type: application/json\r\nAccept: application/json\r\n" +
                        "Connection: close\r\nContent-Length: " + payload.length + "\r\n\r\n";
                socket.getOutputStream().write(header.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                socket.getOutputStream().write(payload);
                socket.getOutputStream().flush();
                byte[] raw = socket.getInputStream().readAllBytes();
                byte[] sep = "\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                int split = -1;
                outer: for (int i=0;i<=raw.length-sep.length;i++) {
                    for (int j=0;j<sep.length;j++) if (raw[i+j]!=sep[j]) continue outer;
                    split=i; break;
                }
                if (split<0) throw new IOException("Missing HTTP response headers from " + ip);
                String head = new String(raw, 0, split, java.nio.charset.StandardCharsets.US_ASCII);
                String[] lines = head.split("\r\n");
                int status = Integer.parseInt(lines[0].split(" ",3)[1]);
                Map<String,List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                for (int i=1;i<lines.length;i++) {
                    int colon=lines[i].indexOf(':');
                    if (colon>0) headers.computeIfAbsent(lines[i].substring(0,colon).trim(), k->new ArrayList<>())
                            .add(lines[i].substring(colon+1).trim());
                }
                byte[] body = java.util.Arrays.copyOfRange(raw,split+4,raw.length);
                if (headers.getOrDefault("Transfer-Encoding",List.of()).stream()
                        .anyMatch(x -> x.toLowerCase(Locale.ROOT).contains("chunked"))) {
                    ByteArrayOutputStream decoded = new ByteArrayOutputStream();
                    int off=0;
                    while (off<body.length) {
                        int end=-1;
                        for(int k=off;k+1<body.length;k++) if(body[k]=='\r' && body[k+1]=='\n') {end=k;break;}
                        if(end<0) throw new IOException("Malformed HTTP chunk");
                        String chunkHeader=new String(body,off,end-off,java.nio.charset.StandardCharsets.US_ASCII);
                        int chunk=Integer.parseInt(chunkHeader.split(";",2)[0].trim(),16);
                        off=end+2;
                        if(chunk==0) break;
                        if(off+chunk+2>body.length) throw new IOException("Truncated HTTP chunk");
                        decoded.write(body,off,chunk);
                        off+=chunk+2;
                    }
                    body=decoded.toByteArray();
                }
                return new Response(status,body,java.net.http.HttpHeaders.of(headers,(k,v)->true));
            }
        } catch (Exception ex) {
            throw new AssertionError("Loopback request " + path + " client #" + addressNumber + " failed",ex);
        }
    }

    Response get(String path, String jwt) { return send("GET",path,jwt,null,null); }
    Response delete(String path, String jwt) { return send("DELETE",path,jwt,null,null); }
    JsonNode node(Response r) {
        try { return om.readTree(r.bytes()); }
        catch (IOException e) { throw new AssertionError("Not JSON: HTTP="+r.status()+" "+r.body(),e); }
    }
    void expect(int expected, Response r) { assertEquals(expected,r.status(),r.body()); }
    /** Reuse one ordinary USER identity across read-only/role checks to avoid exhausting
     * the global login/IP quota when E2E All launches many JUnit classes. */
    Account register(String prefix) {
        Account result = sharedUser;
        if (result != null) return result;
        synchronized (ExtendedE2ESupport.class) {
            if (sharedUser == null) sharedUser = registerDistinct("e2e-shared");
            return sharedUser;
        }
    }
    /** Separate real account for tests that assert isolation or abuse one account. */
    Account registerDistinct(String prefix) {
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
        // Only fixture logins retry. Negative-login/security assertions see the raw
        // status; we never treat a rate-limited password check as proof of 401.
        for (int retry = 0; r.status() == 429 && retry < 3; retry++) {
            long delay = r.headers().firstValue("Retry-After").map(v -> {
                try { return Long.parseLong(v); } catch (NumberFormatException ex) { return 60L; }
            }).orElse(60L);
            System.out.println("[E2E LOGIN] rate limited for " + email + "; waiting for quota reset");
            try { Thread.sleep(Math.max(1L, Math.min(70L, delay + 2L)) * 1000L); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
            r = json("POST","/api/users/login",null,Map.of("email",email,"password",password));
        }
        System.out.println("[E2E LOGIN] email=" + email + " HTTP=" + r.status());
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
        if (Files.isRegularFile(file)) {
            List<String> previous = Files.readAllLines(file);
            if (previous.isEmpty() || previous.get(0).split("\t", -1).length != 2) {
                Files.delete(file);
            } else {
                String[] first = previous.get(0).split("\t", -1);
                // H2 is volatile: a saved fixture file from an earlier service run
                // must not be reused if its users no longer exist in the database.
                Response probe = jsonFromLoopback("/api/users/login",
                        Map.of("email", first[0], "password", first[1]), 900);
                if (probe.status() != 200) Files.delete(file);
            }
        }
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
                int retries = 0;
                while (created.status() == 429 && retries++ < 15) {
                    long seconds = created.headers().firstValue("Retry-After")
                            .map(v -> {try {return Long.parseLong(v);} catch(NumberFormatException e) {return 60L;}})
                            .orElse(60L);
                    try { Thread.sleep(Math.max(1, Math.min(65, seconds + 1)) * 1000L); }
                    catch (InterruptedException ex) {Thread.currentThread().interrupt();throw new AssertionError(ex);}
                    created = json("POST", "/api/users", administrator.token(), Map.of(
                        "email", email, "password", PASSWORD, "role", "USER", "username", username,
                        "firstName", "Load", "lastName", "Fixture"));
                }
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
