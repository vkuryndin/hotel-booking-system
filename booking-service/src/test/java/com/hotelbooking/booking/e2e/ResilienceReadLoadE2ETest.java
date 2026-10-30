package com.hotelbooking.booking.e2e;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Additional gateway/read resilience checks, no server lifecycle mutation. */
@EnabledIfSystemProperty(named="hotel.e2e", matches="true")
@Tag("e2e-resilience")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResilienceReadLoadE2ETest extends ExtendedE2ESupport {
    Account u;Account a;
    Account user(){if(u==null)u=register("readload");return u;}
    Account adminUser(){if(a==null)a=admin();return a;}
    @Test void twoHundredRoomListReadsRespond200() throws Exception {
        String jwt=user().token();List<Callable<Response>> jobs=new ArrayList<>();
        for(int i=0;i<200;i++)jobs.add(()->get("/api/rooms?hotelId=1&page=0&size=3",jwt));
        for(Response r:concurrent(24,jobs))expect(200,r);
    }
    @Test void twoHundredMyBookingsReadsRespond200() throws Exception {
        String jwt=user().token();List<Callable<Response>> jobs=new ArrayList<>();
        for(int i=0;i<200;i++)jobs.add(()->get("/api/bookings/my?page=0&size=3",jwt));
        for(Response r:concurrent(24,jobs))expect(200,r);
    }
    @Test void alternatingUserTokensKeepDataPrivate() {
        Account one=user();Account two=register("readother");
        Response a=get("/api/users/me",one.token());Response b=get("/api/users/me",two.token());
        expect(200,a);expect(200,b);assertNotEquals(node(a).path("id").asText(),node(b).path("id").asText());
    }
    @Test void repeatedAdminListIsAccessible() {
        String jwt=adminUser().token();for(int i=0;i<5;i++)expect(200,get("/api/users?page=0&size=2",jwt));
    }
    @Test void malformedJsonHotelCreationFailsWith400() {
        Response r=send("POST","/api/hotels",adminUser().token(),"{".getBytes(),"application/json");expect(400,r);
    }
    @Test void blankHotelNameIsRejected() {
        Response r=json("POST","/api/hotels",adminUser().token(),Map.of("name"," ","address","Fixture address"));
        assertEquals(400,r.status(),r.body());
    }
    @Test void incompleteLoginDoesNotReturnToken() {
        Response r=json("POST","/api/users/login",null,Map.of("email","somebody@example.test"));
        assertTrue(r.status()>=400&&r.status()<500,r.body());assertFalse(r.body().contains("accessToken"));
    }
    @Test void unsupportedContentTypeDoesNotReturn500() {
        Response r=send("POST","/api/users/login",null,"user=test".getBytes(),"text/plain");
        assertTrue(r.status()==400||r.status()==415,r.body());
    }
    @Test void userCannotCreateRoomEvenWithValidHotel() {
        long hotel=createHotel(adminUser().token());Response r=json("POST","/api/rooms",user().token(),
            Map.of("hotelId",hotel,"number","SHOULD-NOT-CREATE","capacity",2,"category","STANDARD","pricePerNight",100));expect(403,r);
    }
    @Test void strategyReadRemainsAvailableAfterRepeatedCatalogReads() {
        long hotel=createHotel(adminUser().token());String jwt=user().token();
        for(int i=0;i<30;i++)expect(200,get("/api/hotels?page=0&size=2",jwt));
        Response r=get("/api/hotels/"+hotel+"/room-selection-strategy",jwt);expect(200,r);assertTrue(r.body().contains("LEAST_BOOKED"));
    }
}
