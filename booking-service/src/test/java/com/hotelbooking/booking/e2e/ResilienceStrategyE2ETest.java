package com.hotelbooking.booking.e2e;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real per-hotel strategy persistence, authorization and concurrent reads. */
@EnabledIfSystemProperty(named="hotel.e2e",matches="true")
@Tag("e2e-resilience")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResilienceStrategyE2ETest extends ExtendedE2ESupport {
    Account a;Account u;
    Account adminAccount(){ if(a==null)a=admin();return a; }
    Account userAccount(){ if(u==null)u=register("strategy");return u; }
    String path(long id){return "/api/hotels/"+id+"/room-selection-strategy";}
    String read(long h,String jwt){Response r=get(path(h),jwt);expect(200,r);return r.body();}
    void write(long h,String mode){expect(200,json("PUT",path(h),adminAccount().token(),Map.of("strategy",mode)));}
    @Test void newHotelDefaultsToLeastBooked(){long h=createHotel(adminAccount().token());assertTrue(read(h,userAccount().token()).contains("LEAST_BOOKED"));}
    @Test void adminCanSetMostExpensive(){long h=createHotel(adminAccount().token());write(h,"MOST_EXPENSIVE");assertTrue(read(h,userAccount().token()).contains("MOST_EXPENSIVE"));}
    @Test void adminCanRestoreLeastBooked(){long h=createHotel(adminAccount().token());write(h,"MOST_EXPENSIVE");write(h,"LEAST_BOOKED");assertTrue(read(h,userAccount().token()).contains("LEAST_BOOKED"));}
    @Test void differentHotelsKeepIndependentStrategies(){long h1=createHotel(adminAccount().token());long h2=createHotel(adminAccount().token());write(h1,"MOST_EXPENSIVE");assertTrue(read(h2,userAccount().token()).contains("LEAST_BOOKED"));}
    @Test void strategyStatePersistsAcrossSeparateHttpRequests(){long h=createHotel(adminAccount().token());write(h,"MOST_EXPENSIVE");for(int i=0;i<10;i++)assertTrue(read(h,userAccount().token()).contains("MOST_EXPENSIVE"));}
    @Test void userCannotSetMostExpensive(){long h=createHotel(adminAccount().token());expect(403,json("PUT",path(h),userAccount().token(),Map.of("strategy","MOST_EXPENSIVE")));}
    @Test void anonymousCannotSetStrategy(){long h=createHotel(adminAccount().token());expect(401,json("PUT",path(h),null,Map.of("strategy","MOST_EXPENSIVE")));}
    @Test void invalidStrategyDoesNotOverwriteCurrent(){long h=createHotel(adminAccount().token());write(h,"MOST_EXPENSIVE");Response r=json("PUT",path(h),adminAccount().token(),Map.of("strategy","WRONG"));expect(400,r);assertTrue(read(h,userAccount().token()).contains("MOST_EXPENSIVE"));}
    @Test void concurrentReadAfterUpdateIsConsistent() throws Exception {
       long h=createHotel(adminAccount().token());write(h,"MOST_EXPENSIVE");String jwt=userAccount().token();
       List<Callable<Response>> jobs=new ArrayList<>();for(int i=0;i<100;i++)jobs.add(()->get(path(h),jwt));
       for(Response r:concurrent(24,jobs)){expect(200,r);assertTrue(r.body().contains("MOST_EXPENSIVE"));}
    }
    @Test void switchBetweenModesLeavesNoUnexpectedValue() {
        long h=createHotel(adminAccount().token());String jwt=userAccount().token();
        for(int i=0;i<10;i++){String v=i%2==0?"MOST_EXPENSIVE":"LEAST_BOOKED";write(h,v);assertTrue(read(h,jwt).contains(v));}
    }
}
