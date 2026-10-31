package com.hotelbooking.booking.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Disruptive LOCAL fault-injection, run only with the dedicated sagaFaultTest Gradle task.
 * Uses Eureka override, restores UP in finally and AfterAll.
 * Only tests discovery unavailability, not packet loss/slow responses.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = "hotel.e2e", matches = "true")
class HotelBookingSagaFaultE2ETest extends SagaE2ESupport {
    private String instanceId;
    private volatile boolean changedEureka;

    @BeforeAll
    void prepareEureka() {
        assertTrue(EUREKA.startsWith("http://localhost:") && GATEWAY.startsWith("http://localhost:"),
                "Refusing to modify a non-local Eureka registry");
        instanceId=eurekaInstanceId();
        System.out.println("[SAGA FAULT PRECHECK] instance="+instanceId+
                "; EXACTLY ONE local Hotel Service must be registered");
    }

    @AfterAll
    void emergencyRestore() {
        if (changedEureka) {
            try { restoreEureka(instanceId); changedEureka=false; }
            catch (Throwable t) { System.err.println("[SAGA CRITICAL] RESTORE UP FAILED: "+t); }
        }
    }

    void down() {
        // Set flag FIRST: restoration still runs if Eureka status verification throws.
        changedEureka=true;
        setEurekaStatus(instanceId,"OUT_OF_SERVICE");
        await("Hotel route reflects OUT_OF_SERVICE",Duration.ofSeconds(90),()-> {
            R r=get("/api/rooms/"+roomId,owner.token());
            return r.status()==503;
        });
        // Eureka status transitions do not synchronously drain already selected Gateway routes.
        // Give Discovery/Gateway a chance to settle, without pretending that every in-flight
        // business request must fail. Successful operations are reconciled by the tests.
        pause(2500);
        System.out.println("[SAGA FAULT] Gateway route reports 503; in-flight requests may still complete");
    }
    void up() {
        restoreEureka(instanceId);
        changedEureka=false;
        // Circuit Breaker has a 15-second OPEN interval; don't use a doomed probe to confirm recovery.
        pause(17000);
    }
    long restoredBooking(int offset) {
        R r=book(owner,roomPayload(roomId,day(offset),day(offset+1)),UUID.randomUUID());
        return remember(r,owner);
    }

    @Test @Tag("saga-fault") @Order(18) @Timeout(value=9,unit=TimeUnit.MINUTES)
    void hotelOutOfService200BookingsFailWithoutFalseConfirmation() throws Exception {
        long baseline=counter(roomId);
        List<Timed> failed;
        try {
            down();
            failed=parallel("18-Eureka-outage-POST",200,30,i->
                    book(owner,roomPayload(roomId,day(100+i*2),day(101+i*2)),UUID.randomUUID()));
            for (Timed t:failed) {
                R response=t.result();
                assertTrue(response.status()==503 || response.status()==201,
                        "Only explicit outage or fully confirmed booking allowed: " + shortR(response));
                if (response.status()==201) {
                    long id=remember(response,owner);
                    assertEquals("CONFIRMED",js(booking(id,owner)).path("status").asText(),
                            "Successful response must be a real confirmed booking");
                } else assertTrue(Set.of("HOTEL_SERVICE_UNAVAILABLE","BOOKING_SAGA_UNAVAILABLE")
                        .contains(errorCode(response)),shortR(response));
            }
        } finally { if(changedEureka) up(); }
        // Records can be transiently PENDING during Saga compensation/recovery.
        // Any successful in-flight bookings must be cancelled before baseline comparison.
        for (long id:new ArrayList<>(ownedBookings.keySet())) {
            R state=booking(id,owner);
            if(state.status()==200 && "CONFIRMED".equals(js(state).path("status").asText()))
                assertEquals(200,cancel(id,owner).status());
        }
        awaitCounter(roomId,baseline,Duration.ofMinutes(6));
        long id=restoredBooking(900);
        expectStatus(id,owner,"CONFIRMED",CONVERGE);
        assertEquals(200,cancel(id,owner).status());
        expectStatus(id,owner,"CANCELLED",CONVERGE);
        awaitCounter(roomId,baseline,CONVERGE);
    }

    @Test @Tag("saga-fault") @Order(19) @Timeout(value=12,unit=TimeUnit.MINUTES)
    void cancellation100DuringOutageEventuallyCompletes() throws Exception {
        long baseline=counter(roomId);
        LocalDate base=day(1000);
        List<Long> ids=new ArrayList<>();
        // Seed sequentially: when a previous Fault test has just restored Eureka,
        // the Booking->Hotel discovery cache can still be transiently stale.
        for(int i=0;i<100;i++) {
            final int n=i;
            final LocalDate checkIn=base.plusDays(n*3L);
            R created=book(owner,roomPayload(roomId,checkIn,checkIn.plusDays(1)),UUID.randomUUID());
            int retries=0;
            while(created.status()==503 && retries++<30) {
                pause(1000);
                created=book(owner,roomPayload(roomId,checkIn,checkIn.plusDays(1)),UUID.randomUUID());
            }
            assertEquals(201,created.status(),"Seed #"+n+": "+shortR(created));
            ids.add(remember(created,owner));
        }
        awaitCounter(roomId,baseline+100,CONVERGE);
        try {
            down();
            List<Timed> cancellation=parallel("19-cancel-during-outage",100,30,
                    i->cancel(ids.get(i),owner));
            for(int i=0;i<cancellation.size();i++) {
                R result=cancellation.get(i).result();
                assertTrue(result.status()==503 || result.status()==200,shortR(result));
                R state=booking(ids.get(i),owner);
                assertEquals(200,state.status(),shortR(state));
                String expected=result.status()==200 ? "CANCELLED" : "CANCELLING";
                assertEquals(expected,js(state).path("status").asText(),
                        "Cancellation response and persisted state disagree");
            }
        } finally { if(changedEureka) up(); }
        // Real scheduler: age >2min, scan every 60s, max 100 candidates/cycle.
        await("100 CANCELLING -> CANCELLED",Duration.ofMinutes(7),()-> {
            for(long id:ids) {
                R r=booking(id,owner);
                if(r.status()!=200 || !"CANCELLED".equals(js(r).path("status").asText())) return false;
            }
            return true;
        });
        awaitCounter(roomId,baseline,CONVERGE);
        for(int i=0;i<5;i++) assertEquals(200,cancel(ids.get(i),owner).status(),
                "Repeated cancellation after recovery must remain idempotent");
        assertEquals(baseline,counter(roomId));
    }

    @Test @Tag("saga-fault") @Order(20) @Timeout(value=8,unit=TimeUnit.MINUTES)
    void bookedRoomIsNotLostDuringReadAndCreationOutage() throws Exception {
        long baseline=counter(roomId);
        long anchor=restoredBooking(3000);
        assertEquals(baseline+1,counter(roomId));
        try {
            down();
            List<Timed> mix=parallel("20-read-during-outage",200,30,i->
                    i%2==0 ? booking(anchor,owner) :
                            book(owner,roomPayload(roomId,day(3100+i*2),day(3101+i*2)),UUID.randomUUID()));
            for(int i=0;i<mix.size();i++) {
                R response=mix.get(i).result();
                if(i%2==0) {
                    assertEquals(200,response.status(),shortR(response));
                    assertEquals("CONFIRMED",js(response).path("status").asText());
                } else {
                    assertTrue(response.status()==503 || response.status()==201,shortR(response));
                    if(response.status()==201) {
                        long id=remember(response,owner);
                        assertEquals("CONFIRMED",js(booking(id,owner)).path("status").asText());
                    }
                }
            }
        } finally { if(changedEureka) up(); }
        assertEquals("CONFIRMED",js(booking(anchor,owner)).path("status").asText());
        // Clean up legitimate successful in-flight creations before comparing counters.
        for (long id:new ArrayList<>(ownedBookings.keySet())) if(id!=anchor) {
            R state=booking(id,owner);
            if(state.status()==200 && "CONFIRMED".equals(js(state).path("status").asText()))
                assertEquals(200,cancel(id,owner).status());
        }
        awaitCounter(roomId,baseline+1,CONVERGE);
        assertEquals(200,cancel(anchor,owner).status());
        expectStatus(anchor,owner,"CANCELLED",CONVERGE);
        awaitCounter(roomId,baseline,CONVERGE);
    }

    /**
     * Exhaustion takes >=17 minutes due to current exponential 1/2/4/8-minute recovery backoffs.
     * Runs only with sagaExhaustiveTest and requires ADMIN credentials in ignored local secrets file.
     */
    @Test @Tag("saga-exhaustive") @Order(21) @Timeout(value=32,unit=TimeUnit.MINUTES)
    void exhaustedRecoveryAndAdminManualRequeue() {
        org.junit.jupiter.api.Assumptions.assumeTrue(admin != null,
                "Scenario 21 requires booking-service/secrets/saga-e2e-admin.properties");
        long baseline=counter(roomId);
        long id=restoredBooking(5000);
        assertEquals(baseline+1,counter(roomId));
        R denied=get("/api/bookings/recovery/exhausted",owner.token());
        assertEquals(403,denied.status(),"USER must not see recovery admin endpoint");
        R premature=send(GATEWAY,"POST","/api/bookings/"+id+"/recovery/retry",
                admin.token(),null,null,null);
        assertEquals(409,premature.status(),"Only exhausted operations can be requeued");
        try {
            down();
            R cancellation=cancel(id,owner);
            if(cancellation.status()==200) {
                // Eureka cannot guarantee a failure of an already-routed request.
                // This scenario did not actually enter recovery; don't fabricate it.
                assertEquals("CANCELLED",js(booking(id,owner)).path("status").asText());
                org.junit.jupiter.api.Assumptions.abort("Cancellation completed during Eureka transition; recovery exhaustion was not injected");
            }
            assertEquals(503,cancellation.status(),shortR(cancellation));
            expectStatus(id,owner,"CANCELLING",Duration.ofSeconds(30));
            // Recovery scheduler attempts occur with 1/2/4/8 min backoffs.
            await("Saga reaches five failed attempts",Duration.ofMinutes(25),()-> {
                R rows=get("/api/bookings/recovery/exhausted",admin.token());
                if(rows.status()!=200) return false;
                for(JsonNode entry:js(rows)) if(entry.path("id").asLong()==id
                        || entry.path("bookingId").asLong()==id) return true;
                return false;
            });
            R deniedRetry=send(GATEWAY,"POST","/api/bookings/"+id+"/recovery/retry",
                    owner.token(),null,null,null);
            assertEquals(403,deniedRetry.status(),"USER must not requeue exhausted Saga");

            // Requeue while Hotel Service is still OUT_OF_SERVICE. Otherwise the
            // recovery state can change during Eureka restoration and the 17-second
            // circuit-breaker settling interval, making this a timing-dependent test.
            // Requeue only schedules recovery; it does not call Hotel Service.
            R retried=send(GATEWAY,"POST","/api/bookings/"+id+"/recovery/retry",
                    admin.token(),null,null,null);
            assertEquals(202,retried.status(),"ADMIN retry failed for booking " + id
                    + ": " + shortR(retried)
                    + "; exhausted-list-before-retry=" + shortR(get(
                            "/api/bookings/recovery/exhausted",admin.token())));
        } finally { if(changedEureka) up(); }
        await("manual requeue finishes cancellation",Duration.ofMinutes(5),()-> {
            R state=booking(id,owner);
            return state.status()==200 && "CANCELLED".equals(js(state).path("status").asText());
        });
        awaitCounter(roomId,baseline,CONVERGE);
        R exhausted=get("/api/bookings/recovery/exhausted",admin.token());
        assertEquals(200,exhausted.status());
        for(JsonNode row:js(exhausted)) assertFalse(
                row.path("id").asLong()==id || row.path("bookingId").asLong()==id,
                "Finished Saga remains in exhausted list");
    }
}
