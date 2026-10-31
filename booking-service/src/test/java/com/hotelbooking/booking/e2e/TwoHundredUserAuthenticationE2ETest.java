package com.hotelbooking.booking.e2e;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Live E2E: distinct real source-IP addresses keep the shared normal E2E login quota intact. */
@EnabledIfSystemProperty(named="hotel.e2e",matches="true")
@Tag("e2e-200-users")
// Uses distinct genuine local source IPs, preventing interference with other E2E tests.

class TwoHundredUserAuthenticationE2ETest extends ExtendedE2ESupport {
    @Test void twoHundredDistinctUsersLoginAtOnce() throws Exception {
        List<Account> users=suppliedUsers();
        List<Callable<Response>> jobs=new ArrayList<>();
        for (int i=0; i<users.size(); i++) {
            final int index=i;
            Account u=users.get(i);
            jobs.add(() -> jsonFromLoopback("/api/users/login",
                    Map.of("email",u.email(),"password",u.password()),index));
        }
        long start=System.nanoTime();List<Response> responses=concurrent(200,jobs);
        long ok=responses.stream().filter(r -> r.status()==200).count();
        long limited=responses.stream().filter(r -> r.status()==429).count();
        long errors=responses.stream().filter(r -> r.status()>=500).count();
        System.out.printf("[200-user-login] success=%d rateLimited=%d serverErrors=%d elapsedMs=%d%n",
                ok,limited,errors,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
        assertEquals(0,errors,"5xx under 200-user login load");
        assertEquals(200,ok+limited,"Only success or expected rate limit acceptable: " + responses.stream().map(Response::status).toList());
        if (limited==0) {
            Set<String> tokens=new HashSet<>();
            for(Response r:responses)assertTrue(tokens.add(node(r).path("accessToken").asText()),"Duplicate JWT for different users");
            assertEquals(200,tokens.size());
        }
    }
    @Test void twoHundredSimultaneousWrongPasswordsCannotCauseServerFailure() throws Exception {
        List<Account> users=suppliedUsers();List<Callable<Response>> jobs=new ArrayList<>();
        for (int i=0; i<users.size(); i++) {
            final int index=i;
            Account u=users.get(i);
            jobs.add(() -> jsonFromLoopback("/api/users/login",
                    Map.of("email",u.email(),"password","InvalidPassword123!"),index+250));
        }
        List<Response> responses=concurrent(200,jobs);
        for(Response r:responses)assertTrue(r.status()==401||r.status()==429,"Unexpected login response: "+r.status());
    }
}
