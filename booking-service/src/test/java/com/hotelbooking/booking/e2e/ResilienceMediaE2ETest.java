package com.hotelbooking.booking.e2e;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent admin-owned fixtures. Exercises actual multipart upload, file reads and deletes. */
@EnabledIfSystemProperty(named="hotel.e2e",matches="true")
@Tag("e2e-resilience")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResilienceMediaE2ETest extends ExtendedE2ESupport {
    Account a;Account u;
    Account adminAccount(){ if(a==null)a=admin(); return a; }
    Account userAccount(){ if(u==null)u=register("media"); return u; }
    String[] fixture(boolean room){
        Account admin=adminAccount();long hotel=createHotel(admin.token());
        long id=room?createRoom(admin.token(),hotel):hotel;
        return new String[]{room?"/api/rooms/"+id+"/photos":"/api/hotels/"+id+"/photos",admin.token()};
    }
    @Test void hotelPhotosStartEmpty() {String[] f=fixture(false);expect(200,get(f[0],f[1]));assertEquals(0,node(get(f[0],f[1])).size());}
    @Test void roomPhotosStartEmpty() {String[] f=fixture(true);expect(200,get(f[0],f[1]));assertEquals(0,node(get(f[0],f[1])).size());}
    @Test void hotelPhotoUploadAndExactDownload() {roundtrip(false);}
    @Test void roomPhotoUploadAndExactDownload() {roundtrip(true);}
    void roundtrip(boolean room){String[] f=fixture(room);Response p=upload(f[0],f[1],PNG,"photo.png","image/png");expect(201,p);
        long id=node(p).path("id").asLong();assertTrue(id>0);Response downloaded=get(f[0]+"/"+id+"/file",f[1]);expect(200,downloaded);assertArrayEquals(PNG,downloaded.bytes());
        expect(204,delete(f[0]+"/"+id,f[1]));assertEquals(0,node(get(f[0],f[1])).size());}
    @Test void userCannotUploadHotelPhoto() {String[] f=fixture(false);expect(403,upload(f[0],userAccount().token(),PNG,"photo.png","image/png"));}
    @Test void userCannotUploadRoomPhoto() {String[] f=fixture(true);expect(403,upload(f[0],userAccount().token(),PNG,"photo.png","image/png"));}
    @Test void anonymousCannotUploadPhoto() {String[] f=fixture(true);expect(401,upload(f[0],null,PNG,"photo.png","image/png"));}
    @Test void invalidHotelImageIsRejected() {invalidImage(false);}
    @Test void invalidRoomImageIsRejected() {invalidImage(true);}
    void invalidImage(boolean room){String[] f=fixture(room);Response p=upload(f[0],f[1],"bad".getBytes(),"photo.png","image/png");expect(400,p);assertEquals(0,node(get(f[0],f[1])).size());}
    @Test void deletedHotelPhotoNotDownloadable() {deletedPhoto(false);}
    @Test void deletedRoomPhotoNotDownloadable() {deletedPhoto(true);}
    void deletedPhoto(boolean room){String[] f=fixture(room);Response p=upload(f[0],f[1],PNG,"p.png","image/png");expect(201,p);
       long id=node(p).path("id").asLong();expect(204,delete(f[0]+"/"+id,f[1]));expect(404,get(f[0]+"/"+id+"/file",f[1]));}
    @Test void userCannotDeleteHotelPhoto() {cannotDelete(false);}
    @Test void userCannotDeleteRoomPhoto() {cannotDelete(true);}
    void cannotDelete(boolean room){String[] f=fixture(room);Response p=upload(f[0],f[1],PNG,"p.png","image/png");expect(201,p);
      long id=node(p).path("id").asLong();expect(403,delete(f[0]+"/"+id,userAccount().token()));expect(204,delete(f[0]+"/"+id,f[1]));}
    @Test void concurrentDownloadsKeepBytesIntact() throws Exception {
       String[] f=fixture(true);Response p=upload(f[0],f[1],PNG,"p.png","image/png");expect(201,p);
       String url=f[0]+"/"+node(p).path("id").asLong()+"/file";
       List<Callable<Response>> jobs=new ArrayList<>();for(int i=0;i<100;i++)jobs.add(()->get(url,f[1]));
       for(Response r:concurrent(25,jobs)){expect(200,r);assertArrayEquals(PNG,r.bytes());}
       expect(204,delete(f[0]+"/"+node(p).path("id").asLong(),f[1]));
    }
    @Test void roomDeleteWithPhotoHasNoDanglingRecord() {
       Account admin=adminAccount();long h=createHotel(admin.token());long r=createRoom(admin.token(),h);
       String url="/api/rooms/"+r+"/photos";Response p=upload(url,admin.token(),PNG,"p.png","image/png");expect(201,p);
       expect(204,delete("/api/rooms/"+r,admin.token()));expect(404,get("/api/rooms/"+r,admin.token()));
       assertEquals(404,get(url+"/"+node(p).path("id").asLong()+"/file",admin.token()).status());
    }
}
