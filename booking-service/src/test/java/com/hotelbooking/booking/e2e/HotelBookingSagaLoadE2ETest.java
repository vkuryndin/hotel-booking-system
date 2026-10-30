package com.hotelbooking.booking.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Six non-disruptive Saga / counter load E2E tests. Run on live services, no mocks. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = "hotel.e2e", matches = "true")
class HotelBookingSagaLoadE2ETest extends SagaE2ESupport {

    @Test @Order(12)
    void changedPayloadWithSameIdempotencyKey100() throws Exception {
        LocalDate in=day(10);
        long baseline=counter(roomId);
        UUID key=UUID.randomUUID();
        Map<String,Object> original=roomPayload(roomId,in,in.plusDays(2));
        R first=book(owner,original,key);
        long id=remember(first,owner);
        awaitCounter(roomId,baseline+1,CONVERGE);
        Map<String,Object> modified=roomPayload(roomId,in.plusDays(7),in.plusDays(9));
        List<Timed> different=parallel("12-key-payload-conflict",100,25,
                i->book(owner,modified,key));
        statusAll(different,409);
        for (Timed t:different) assertEquals("IDEMPOTENCY_KEY_CONFLICT",errorCode(t.result()));
        R replay=book(owner,original,key);
        assertEquals(201,replay.status(),shortR(replay));
        assertEquals(id,js(replay).path("id").asLong(),"Idempotent original booking changed");
        awaitCounter(roomId,baseline+1,CONVERGE);
        assertEquals(200,cancel(id,owner).status());
        expectStatus(id,owner,"CANCELLED",CONVERGE);
        awaitCounter(roomId,baseline,CONVERGE);
    }

    @Test @Order(13)
    void reserveCancelRebook300Cycles() {
        LocalDate in=day(30);
        long baseline=counter(roomId);
        for (int i=0;i<300;i++) {
            long id=remember(book(owner,roomPayload(roomId,in,in.plusDays(1)),UUID.randomUUID()),owner);
            assertEquals(baseline+1,counter(roomId),"Missing +1 on cycle " + i);
            R cancelled=cancel(id,owner);
            assertEquals(200,cancelled.status(),shortR(cancelled));
            expectStatus(id,owner,"CANCELLED",CONVERGE);
            assertEquals(baseline,counter(roomId),"Missing -1 on cycle " + i);
        }
        System.out.println("[SAGA 13] passed 300 reservation/cancellation/rebooking cycles");
    }

    @Test @Order(14)
    void cancelVsNewBookingRace200Requests() throws Exception {
        LocalDate in=day(50);
        long baseline=counter(roomId);
        long active=remember(book(owner,roomPayload(roomId,in,in.plusDays(1)),UUID.randomUUID()),owner);
        for (int i=0;i<100;i++) {
            long old=active;
            CompletableFuture<R> cancellation=CompletableFuture.supplyAsync(()->cancel(old,owner));
            CompletableFuture<R> contender=CompletableFuture.supplyAsync(()->
                    book(owner,roomPayload(roomId,in,in.plusDays(1)),UUID.randomUUID()));
            R c=cancellation.get(30,TimeUnit.SECONDS);
            R n=contender.get(30,TimeUnit.SECONDS);
            assertEquals(200,c.status(),"Cancellation failed round " + i + ": " + shortR(c));
            expectStatus(old,owner,"CANCELLED",CONVERGE);
            if (n.status()==201) {
                active=remember(n,owner);
            } else {
                assertEquals(409,n.status(),"Unexpected concurrent booking result round " + i + ": " + shortR(n));
                assertEquals("ROOM_ALREADY_BOOKED",errorCode(n),shortR(n));
                active=remember(book(owner,roomPayload(roomId,in,in.plusDays(1)),UUID.randomUUID()),owner);
            }
            assertEquals(baseline+1,counter(roomId),"Double count in cancel/rebook race " + i);
        }
        R end=cancel(active,owner);
        assertEquals(200,end.status(),shortR(end));
        expectStatus(active,owner,"CANCELLED",CONVERGE);
        awaitCounter(roomId,baseline,CONVERGE);
        System.out.println("[SAGA 14] 200 racing requests (100 cancel + 100 create)");
    }

    @Test @Order(15)
    void fullyBookedHotelAutoSelect500() throws Exception {
        LocalDate in=day(100);
        List<Long> available=new ArrayList<>();
        R recommendation=get("/api/rooms/recommend?hotelId="+hotelId+
                "&checkIn="+in+"&checkOut="+in.plusDays(2)+"&guestCount=1",owner.token());
        assertEquals(200,recommendation.status(),shortR(recommendation));
        for (JsonNode item:js(recommendation)) available.add(item.path("id").asLong());
        assertFalse(available.isEmpty(),"No inventory for full-hotel test");
        Map<Long,Long> before=new LinkedHashMap<>();
        for (long id:available) before.put(id,counter(id));
        List<Long> anchors=new ArrayList<>();
        for (long id:available) anchors.add(remember(book(owner,roomPayload(id,in,in.plusDays(2)),UUID.randomUUID()),owner));
        R empty=get("/api/rooms/recommend?hotelId="+hotelId+
                "&checkIn="+in+"&checkOut="+in.plusDays(2)+"&guestCount=1",owner.token());
        assertEquals(200,empty.status(),shortR(empty));
        assertTrue(js(empty).isEmpty(),"Not all rooms are actually booked; fixture is invalid");
        List<Timed> attempts=parallel("15-full-hotel-auto",500,50,
                i->book(owner,autoPayload(in,in.plusDays(2)),UUID.randomUUID()));
        statusAll(attempts,409);
        for (Timed t:attempts) assertEquals("NO_AVAILABLE_ROOMS",errorCode(t.result()));
        for (long id:available) assertEquals(before.get(id)+1,counter(id),"Unexpected counter increment for " + id);
        for (long id:anchors) assertEquals(200,cancel(id,owner).status());
        for (long id:available) awaitCounter(id,before.get(id),CONVERGE);
    }

    @Test @Order(16)
    void exactTimesBookedAfter250ReservationsAndCancellation() throws Exception {
        long initial=counter(roomId);
        LocalDate base=day(200);
        List<Long> booked=new ArrayList<>();
        // 10 bounded batches limit H2 lock contention while keeping real concurrency.
        for (int batch=0;batch<10;batch++) {
            final int offset=batch*25;
            List<Timed> created=parallel("16-book-batch-"+batch,25,10,i->
                    book(owner,roomPayload(roomId,base.plusDays((offset+i)*3L),
                            base.plusDays((offset+i)*3L+1)),UUID.randomUUID()));
            statusAll(created,201);
            for (Timed m:created) booked.add(remember(m.result(),owner));
        }
        assertEquals(250,booked.size());
        awaitCounter(roomId,initial+250,CONVERGE);
        for (int batch=0;batch<10;batch++) {
            final int offset=batch*25;
            List<Timed> cancelled=parallel("16-cancel-batch-"+batch,25,10,i->cancel(booked.get(offset+i),owner));
            statusAll(cancelled,200);
        }
        for (long id:booked) expectStatus(id,owner,"CANCELLED",CONVERGE);
        awaitCounter(roomId,initial,CONVERGE);
        // Replayed cancellation cannot produce a second decrement.
        for (int i=0;i<10;i++) assertEquals(200,cancel(booked.get(i),owner).status());
        assertEquals(initial,counter(roomId));
    }

    @Test @Order(17)
    void mixedFiveMinuteSagaSoak() throws Exception {
        long baseline=counter(roomId);
        LocalDate base=day(1500);
        List<Long> rolling=new ArrayList<>();
        for (int j=0;j<5;j++) rolling.add(remember(book(owner,
                roomPayload(roomId,base.plusDays(j*3L),base.plusDays(j*3L+1)),UUID.randomUUID()),owner));
        final long limit=System.nanoTime()+Duration.ofMinutes(5).toNanos();
        int batches=0;
        int ops=0;
        try {
            while (System.nanoTime()<limit) {
                final int n=batches++;
                List<Long> previous=List.copyOf(rolling);
                List<Timed> results=parallel("17-soak-batch-"+n,20,12,i->switch(i%4) {
                    case 0 -> get("/api/hotels?page=0&size=10",owner.token());
                    case 1 -> get("/api/bookings/my?page=0&size=10",owner.token());
                    case 2 -> book(owner,roomPayload(roomId,base.plusDays(30L+n*30L+i),
                            base.plusDays(31L+n*30L+i)),UUID.randomUUID());
                    default -> cancel(previous.get(i/4),owner);
                });
                List<Long> current=new ArrayList<>();
                for (int i=0;i<results.size();i++) {
                    R r=results.get(i).result();
                    int type=i%4;
                    int expected=(type==2?201:200);
                    assertEquals(expected,r.status(),"soak batch="+n+" index="+i+" "+shortR(r));
                    if(type==2) current.add(remember(r,owner));
                }
                assertEquals(5,current.size());
                rolling=current;
                ops+=20;
                if (batches%10==0) assertEquals(baseline+5,counter(roomId),"Soak counter drift");
                // Pacing: continuous load for five minutes, not a one-second request burst.
                pause(800);
            }
        } finally {
            for (long id:rolling) {
                R r=booking(id,owner);
                if(r.status()==200 && "CONFIRMED".equals(js(r).path("status").asText()))
                    cancel(id,owner);
            }
        }
        assertTrue(ops>=500,"Soak did not execute enough operations: "+ops);
        awaitCounter(roomId,baseline,CONVERGE);
        System.out.println("[SAGA 17] five-minute soak finished: operations="+ops+" batches="+batches);
    }
}
