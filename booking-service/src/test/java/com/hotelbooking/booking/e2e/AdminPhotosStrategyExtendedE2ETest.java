package com.hotelbooking.booking.e2e;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Run separately from fault-injection E2E; creates isolated data. */
@EnabledIfSystemProperty(named="hotel.e2e",matches="true")
@Tag("e2e-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminPhotosStrategyExtendedE2ETest extends ExtendedE2ESupport {
    private Account cachedUser;
    private Account cachedAdmin;
    Account user() { if (cachedUser == null) cachedUser = register("adminsuite"); return cachedUser; }
    Account adminUser() { if (cachedAdmin == null) cachedAdmin = admin(); return cachedAdmin; }
    @Test void nonAdminCannotCreateHotelsOrRooms() {
        Account user=user();
        Response hotel=json("POST","/api/hotels",user.token(),Map.of("name","Forbidden","address","Forbidden"));
        expect(403,hotel);
        Response room=json("POST","/api/rooms",user.token(),Map.of("hotelId",1,"number","X1","capacity",2,"pricePerNight",100));
        expect(403,room);
    }
    @Test void anonymousCannotMutateHotel() {
        Response r=json("POST","/api/hotels",null,Map.of("name","Forbidden","address","Forbidden"));
        expect(401,r);
    }
    @Test void administratorCreatesHotelAndRoomsAndCanReadThem() {
        Account admin=adminUser();long h=createHotel(admin.token());long room=createRoom(admin.token(),h);
        expect(200,get("/api/hotels/"+h,admin.token()));
        expect(200,get("/api/rooms/"+room,admin.token()));
    }
    @Test void strategyIsPerHotelAndAdminOnly() {
        Account a=adminUser();Account u=user();
        long h1=createHotel(a.token());long h2=createHotel(a.token());
        String p1="/api/hotels/"+h1+"/room-selection-strategy";
        String p2="/api/hotels/"+h2+"/room-selection-strategy";
        expect(403,json("PUT",p1,u.token(),Map.of("strategy","MOST_EXPENSIVE")));
        expect(200,json("PUT",p1,a.token(),Map.of("strategy","MOST_EXPENSIVE")));
        assertTrue(get(p1,u.token()).body().contains("MOST_EXPENSIVE"));
        assertTrue(get(p2,u.token()).body().contains("LEAST_BOOKED"));
        expect(200,json("PUT",p1,a.token(),Map.of("strategy","LEAST_BOOKED")));
    }
    @Test void hotelPhotoLifecycleAndAuthorization() {
        Account a=adminUser();Account u=user();long h=createHotel(a.token());
        String url="/api/hotels/"+h+"/photos";
        expect(403,upload(url,u.token(),PNG,"photo.png","image/png"));
        Response uploaded=upload(url,a.token(),PNG,"photo.png","image/png");expect(201,uploaded);
        long id=node(uploaded).path("id").asLong();assertTrue(id>0);
        expect(200,get(url,u.token()));
        Response file=get(url+"/"+id+"/file",u.token());expect(200,file);
        assertArrayEquals(PNG,file.bytes());
        expect(200,send("PATCH",url+"/"+id+"/primary",a.token(),null,null));
        expect(200,send("PATCH",url+"/"+id+"/order?sortOrder=1",a.token(),null,null));
        expect(204,delete(url+"/"+id,a.token()));
        assertEquals(0,node(get(url,a.token())).size());
    }
    @Test void roomPhotoLifecycleAndAuthorization() {
        Account a=adminUser();Account u=user();
        long h=createHotel(a.token());long room=createRoom(a.token(),h);
        String url="/api/rooms/"+room+"/photos";
        expect(403,upload(url,u.token(),PNG,"photo.png","image/png"));
        Response uploaded=upload(url,a.token(),PNG,"photo.png","image/png");expect(201,uploaded);
        long id=node(uploaded).path("id").asLong();assertTrue(id>0);
        expect(200,get(url,u.token()));
        Response file=get(url+"/"+id+"/file",u.token());expect(200,file);assertArrayEquals(PNG,file.bytes());
        expect(200,send("PATCH",url+"/"+id+"/primary",a.token(),null,null));
        expect(200,send("PATCH",url+"/"+id+"/order?sortOrder=2",a.token(),null,null));
        expect(204,delete(url+"/"+id,a.token()));
        assertEquals(0,node(get(url,a.token())).size());
    }
    @Test void unsupportedImageMustBeRejected() {
        Account a=adminUser();long h=createHotel(a.token());long r=createRoom(a.token(),h);
        byte[] invalid="this-is-not-a-picture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Response hotel=upload("/api/hotels/"+h+"/photos",a.token(),invalid,"bad.jpg","image/jpeg");
        Response room=upload("/api/rooms/"+r+"/photos",a.token(),invalid,"bad.jpg","image/jpeg");
        assertEquals(400,hotel.status(),hotel.body());assertEquals(400,room.status(),room.body());
    }
    @Test void deletingRoomRemovesAssociatedPhotoMetadata() {
        Account a=adminUser();long h=createHotel(a.token());long r=createRoom(a.token(),h);
        Response p=upload("/api/rooms/"+r+"/photos",a.token(),PNG,"image.png","image/png");expect(201,p);
        expect(204,delete("/api/rooms/"+r,a.token()));
        assertEquals(404,get("/api/rooms/"+r,a.token()).status());
    }
}
