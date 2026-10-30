package com.hotelbooking.booking.e2e;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Live Saga invariants with isolated far-future dates; not a fault-injection suite. */
@EnabledIfSystemProperty(named="hotel.e2e", matches="true")
@Tag("e2e-resilience")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResilienceBookingE2ETest extends SagaE2ESupport {
    @Test void bookingAndCancelRestoreCounter() {
        long initial=counter(roomId);LocalDate in=day(3000);
        long id=remember(book(owner,roomPayload(roomId,in,in.plusDays(2)),UUID.randomUUID()),owner);
        awaitCounter(roomId,initial+1,CONVERGE);
        assertEquals(200,cancel(id,owner).status());expectStatus(id,owner,"CANCELLED",CONVERGE);
        awaitCounter(roomId,initial,CONVERGE);
    }
    @Test void simultaneous200AttemptsSameRoomCannotDoubleBook() throws Exception {
        LocalDate in=day(3050);long before=counter(roomId);
        List<Timed> result=parallel("200-same-room",200,24,i->book(owner,roomPayload(roomId,in,in.plusDays(2)),UUID.randomUUID()));
        long created=result.stream().filter(t->t.result().status()==201).count();
        assertEquals(1,created,"Exactly one 201 required: "+result.stream().map(t->t.result().status()).toList());
        for(Timed r:result)assertTrue(r.result().status()==201||r.result().status()==409,shortR(r.result()));
        awaitCounter(roomId,before+1,CONVERGE);
        for(Timed t:result)if(t.result().status()==201){long id=remember(t.result(),owner);assertEquals(200,cancel(id,owner).status());}
        awaitCounter(roomId,before,CONVERGE);
    }
    @Test void hundredReplaysSameKeyProduceSingleReservation() throws Exception {
        LocalDate in=day(3100);long before=counter(roomId);UUID key=UUID.randomUUID();
        Map<String,Object> payload=roomPayload(roomId,in,in.plusDays(2));
        List<Timed> result=parallel("100-same-key",100,24,i->book(owner,payload,key));
        Set<Long> ids=new HashSet<>();
        for(Timed t:result){R r=t.result();assertTrue(r.status()==201||r.status()==409,shortR(r));
            if(r.status()==201)ids.add(js(r).path("id").asLong());}
        R replay=book(owner,payload,key);assertEquals(201,replay.status(),shortR(replay));
        long id=remember(replay,owner);ids.add(id);assertEquals(1,ids.size(),"Multiple bookings for same key");
        awaitCounter(roomId,before+1,CONVERGE);assertEquals(200,cancel(id,owner).status());
        awaitCounter(roomId,before,CONVERGE);
    }
    @Test void differentPayloadSameKeyCannotCreateSecondBooking() {
        LocalDate in=day(3200);long before=counter(roomId);UUID key=UUID.randomUUID();
        R first=book(owner,roomPayload(roomId,in,in.plusDays(2)),key);long id=remember(first,owner);
        R second=book(owner,roomPayload(roomId,in.plusDays(4),in.plusDays(5)),key);
        assertEquals(409,second.status(),shortR(second));
        awaitCounter(roomId,before+1,CONVERGE);assertEquals(200,cancel(id,owner).status());awaitCounter(roomId,before,CONVERGE);
    }
    @Test void invalidDateRangeRejectedWithoutIncrementingCounter() {
        long before=counter(roomId);LocalDate in=day(3300);
        R r=book(owner,roomPayload(roomId,in,in.minusDays(1)),UUID.randomUUID());
        assertTrue(r.status()==400||r.status()==422,shortR(r));assertEquals(before,counter(roomId));
    }
    @Test void zeroGuestCountRejected() {
        LocalDate in=day(3350);
        R r=book(owner,Map.of("roomId",roomId,"checkIn",in.toString(),"checkOut",in.plusDays(1).toString(),"guestCount",0),UUID.randomUUID());
        assertEquals(400,r.status(),shortR(r));
    }
    @Test void excessiveGuestCountRejected() {
        LocalDate in=day(3400);
        R r=book(owner,Map.of("roomId",roomId,"checkIn",in.toString(),"checkOut",in.plusDays(1).toString(),"guestCount",21),UUID.randomUUID());
        assertEquals(400,r.status(),shortR(r));
    }
    @Test void noKeyBookingMustBeRejected() {
        LocalDate in=day(3450);
        R r=post("/api/bookings",roomPayload(roomId,in,in.plusDays(1)),owner.token(),null);
        assertEquals(400,r.status(),shortR(r));
    }
    @Test void hundredParallelCounterReadsAgree() throws Exception {
        long baseline=counter(roomId);
        List<Timed> got=parallel("counter-read",100,24,i->get("/api/rooms/"+roomId,owner.token()));
        for(Timed t:got){assertEquals(200,t.result().status(),shortR(t.result()));
            assertEquals(baseline,js(t.result()).path("timesBooked").asLong());}
    }
}
