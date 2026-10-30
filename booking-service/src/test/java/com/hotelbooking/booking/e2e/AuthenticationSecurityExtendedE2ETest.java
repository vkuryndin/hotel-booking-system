package com.hotelbooking.booking.e2e;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import java.util.concurrent.Callable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="hotel.e2e",matches="true")
@Tag("e2e-security")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthenticationSecurityExtendedE2ETest extends ExtendedE2ESupport {
    private Account cached;
    Account testUser() { if (cached==null) cached=register("securitysuite"); return cached; }
    @Test @Order(1) void wrongPasswordRejectedAndValidPasswordWorks() {
        Account a=testUser();
        expect(401,json("POST","/api/users/login",null,Map.of("email",a.email(),"password","DefinitelyWrong123!")));
        assertFalse(login(a.email(),a.password()).isBlank());
    }
    @Test @Order(99) void distributedAttemptsAgainstOneAccountAreBounded() throws Exception {
        Account a=testUser();
        List<Callable<Response>> requests=new ArrayList<>();
        for(int i=0;i<20;i++) requests.add(() -> json("POST","/api/users/login",null,
                Map.of("email",a.email(),"password","WrongPassword123!")));
        List<Response> all=concurrent(20,requests);
        long limited=all.stream().filter(r -> r.status()==429).count();
        assertTrue(limited>0,"Account brute-force protection did not return 429: "+all.stream().map(Response::status).toList());
        for(Response r:all)assertTrue(r.status()==401||r.status()==429,"Unexpected HTTP "+r.status()+" "+r.body());
    }
    @Test @Order(2) void garbageJwtAndMissingJwtCannotReadProtectedApi() {
        expect(401,get("/api/users/me",null));
        expect(401,get("/api/users/me","invalid.jwt.contents"));
    }
    @Test @Order(3) void ordinaryUserCannotReadAdminUsers() {
        Account a=testUser();expect(403,get("/api/users",a.token()));
    }
    @Test @Order(4) void sameJwtConcurrentChecksDoNotReturn5xx() throws Exception {
        Account a=testUser();
        List<Callable<Response>> requests=new ArrayList<>();
        for(int i=0;i<100;i++)requests.add(() -> get("/api/users/me",a.token()));
        List<Response> all=concurrent(40,requests);
        for(Response r:all)expect(200,r);
    }
}
