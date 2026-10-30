package com.hotelbooking.booking.e2e;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolate from security-rate-limit tests; see README for fixture and rate-limit requirements. */
@EnabledIfSystemProperty(named="hotel.e2e",matches="true")
@Tag("e2e-200-users")
// 200 POST /login requests from a single client IP inevitably consume the shared
// production login quota (20/min) and break unrelated tests. Must be explicitly
// opted in on a dedicated run, not under the user's normal E2E All configuration.
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hotel.e2e.exclusive-rate-limit", matches="true")
class TwoHundredUserAuthenticationE2ETest extends ExtendedE2ESupport {
    @Test void twoHundredDistinctUsersLoginAtOnce() throws Exception {
        List<Account> users=suppliedUsers();
        List<Callable<Response>> jobs=new ArrayList<>();
        for(Account u:users)jobs.add(() -> json("POST","/api/users/login",null,
                Map.of("email",u.email(),"password",u.password())));
        long start=System.nanoTime();List<Response> responses=concurrent(200,jobs);
        long ok=responses.stream().filter(r -> r.status()==200).count();
        long limited=responses.stream().filter(r -> r.status()==429).count();
        long errors=responses.stream().filter(r -> r.status()>=500).count();
        System.out.printf("[200-user-login] success=%d rateLimited=%d serverErrors=%d elapsedMs=%d%n",
                ok,limited,errors,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
        assertEquals(0,errors,"5xx under 200-user login load");
        assertEquals(200,ok+limited,"Only success or expected rate limit acceptable: " + responses.stream().map(Response::status).toList());
        // With production IP rate limits all clients behind one NAT can legitimately see 429.
        // For a separate throughput test, use a dedicated isolated E2E config with suitable IP limits.
        if (limited==0) {
            Set<String> tokens=new HashSet<>();
            for(Response r:responses)assertTrue(tokens.add(node(r).path("accessToken").asText()),"Duplicate JWT for different users");
            assertEquals(200,tokens.size());
        }
    }
    @Test void twoHundredSimultaneousWrongPasswordsCannotCauseServerFailure() throws Exception {
        List<Account> users=suppliedUsers();List<Callable<Response>> jobs=new ArrayList<>();
        for(Account u:users)jobs.add(() -> json("POST","/api/users/login",null,
                Map.of("email",u.email(),"password","InvalidPassword123!")));
        List<Response> responses=concurrent(200,jobs);
        for(Response r:responses)assertTrue(r.status()==401||r.status()==429,"Unexpected login response: "+r.status());
    }
}
