package com.hotelbooking.booking.e2e;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Live, non-disruptive Gateway/security contract tests. Run separately from fault injection. */
@EnabledIfSystemProperty(named="hotel.e2e", matches="true")
@Tag("e2e-resilience")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResilienceSecurityE2ETest extends ExtendedE2ESupport {
    private Account actor;
    Account actor() { if (actor == null) actor = register("resilience"); return actor; }

    @Test void anonymousProfileRejected() { expect(401, get("/api/users/me", null)); }
    @Test void anonymousBookingsRejected() { expect(401, get("/api/bookings/my", null)); }
    @Test void anonymousHotelMutationRejected() {
        expect(401,json("POST","/api/hotels",null,Map.of("name","denied","address","denied")));
    }
    @Test void malformedJwtCannotOpenProfile() { expect(401,get("/api/users/me","a.b.c")); }
    @Test void malformedJwtCannotListBookings() { expect(401,get("/api/bookings/my","a.b.c")); }
    @Test void malformedJwtCannotReadHotelCatalog() { expect(401,get("/api/hotels", "a.b.c")); }
    @Test void userCannotListAllUsers() { expect(403,get("/api/users", actor().token())); }
    @Test void userCannotListAllBookings() { expect(403,get("/api/bookings", actor().token())); }
    @Test void userCannotCreateHotel() {
        expect(403,json("POST","/api/hotels",actor().token(),Map.of("name","forbidden","address","x")));
    }
    @Test void userCannotModifyStrategy() {
        expect(403,json("PUT","/api/hotels/1/room-selection-strategy",actor().token(),Map.of("strategy","MOST_EXPENSIVE")));
    }
    @Test void authenticatedCatalogRespondsNormally() { expect(200,get("/api/hotels?page=0&size=2",actor().token())); }
    @Test void authenticatedOwnBookingsRespondNormally() { expect(200,get("/api/bookings/my",actor().token())); }

    @Test void twoHundredConcurrentProfileReadsHaveNoErrors() throws Exception {
        String jwt=actor().token();List<Callable<Response>> calls=new ArrayList<>();
        for(int i=0;i<200;i++) calls.add(()->get("/api/users/me",jwt));
        List<Response> r=concurrent(32,calls);
        for(Response x:r) expect(200,x);
    }
    @Test void twoHundredConcurrentCatalogReadsHaveNoErrors() throws Exception {
        String jwt=actor().token();List<Callable<Response>> calls=new ArrayList<>();
        for(int i=0;i<200;i++) calls.add(()->get("/api/hotels?page=0&size=3",jwt));
        List<Response> r=concurrent(32,calls);
        for(Response x:r) expect(200,x);
    }
    @Test void twoHundredInvalidTokensNeverSucceed() throws Exception {
        List<Callable<Response>> calls=new ArrayList<>();
        for(int i=0;i<200;i++) calls.add(()->get("/api/users/me","garbage."+UUID.randomUUID()+".jwt"));
        for(Response x:concurrent(32,calls)) expect(401,x);
    }
    @Test void wrongPasswordNeverIssuesToken() {
        Account u=actor();Response r=json("POST","/api/users/login",null,
            Map.of("email",u.email(),"password","WrongOneTimePassword123!"));
        expect(401,r); assertFalse(r.body().contains("accessToken"));
    }
    @Test void nonexistentAccountNeverIssuesToken() {
        Response r=json("POST","/api/users/login",null,
            Map.of("email","unknown."+UUID.randomUUID()+"@example.test","password","WrongPassword123!"));
        assertTrue(r.status()==401 || r.status()==429, r.body());
        assertFalse(r.body().contains("accessToken"));
    }
    @Test void invalidJsonCannotAuthenticate() {
        Response r=send("POST","/api/users/login",null,"{".getBytes(),"application/json");
        assertEquals(400,r.status(),r.body());
    }
    @Test void unknownRouteMustNotLeakServerFailure() {
        Response r=get("/api/users/nonexistent-endpoint-"+runId,actor().token());
        assertTrue(r.status()==404 || r.status()==405,r.body());
    }
}
