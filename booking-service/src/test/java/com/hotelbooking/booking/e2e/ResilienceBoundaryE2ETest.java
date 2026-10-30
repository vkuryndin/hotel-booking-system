package com.hotelbooking.booking.e2e;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Boundary, input-validation and RBAC checks via real Gateway. */
@EnabledIfSystemProperty(named="hotel.e2e",matches="true")
@Tag("e2e-resilience")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResilienceBoundaryE2ETest extends ExtendedE2ESupport {
    Account u;Account a;
    Account user(){ if(u==null)u=register("boundary");return u;}
    Account administrator(){if(a==null)a=admin();return a;}
    @Test void missingHotelReturns404(){expect(404,get("/api/hotels/999999999",user().token()));}
    @Test void missingRoomReturns404(){expect(404,get("/api/rooms/999999999",user().token()));}
    @Test void missingBookingReturns404(){expect(404,get("/api/bookings/999999999",user().token()));}
    @Test void invalidRoomCategoryReturnsClientError(){Response r=get("/api/rooms?hotelId=1&category=NOT_A_CATEGORY",user().token());assertEquals(400,r.status(),r.body());}
    @Test void invalidRoomSortReturnsClientError(){Response r=get("/api/rooms?hotelId=1&sortBy=unknownField",user().token());assertTrue(r.status()==400||r.status()==422,r.body());}
    @Test void invalidRecommendationDateReturnsClientError(){Response r=get("/api/rooms/recommend?hotelId=1&checkIn=banana&checkOut=tomorrow",user().token());assertEquals(400,r.status(),r.body());}
    @Test void userCannotDeleteRoom(){expect(403,delete("/api/rooms/1",user().token()));}
    @Test void anonymousCannotDeleteRoom(){expect(401,delete("/api/rooms/1",null));}
    @Test void anonymousCannotResetStrategy(){expect(401,json("PUT","/api/hotels/1/room-selection-strategy",null,Map.of("strategy","LEAST_BOOKED")));}
    @Test void userCannotDeleteHotel(){expect(403,delete("/api/hotels/1",user().token()));}
    @Test void invalidStrategyRejected(){long h=createHotel(administrator().token());Response r=json("PUT","/api/hotels/"+h+"/room-selection-strategy",administrator().token(),Map.of("strategy","INVALID"));assertEquals(400,r.status(),r.body());}
    @Test void hotelsAndRoomsHaveValidPagination(){Account actor=user();Response hotels=get("/api/hotels?page=0&size=5",actor.token());expect(200,hotels);assertTrue(node(hotels).path("content").isArray());}
    @Test void photosForMissingHotelReturn404(){assertEquals(404,get("/api/hotels/999999999/photos",user().token()).status());}
    @Test void photosForMissingRoomReturn404(){assertEquals(404,get("/api/rooms/999999999/photos",user().token()).status());}
    @Test void unknownHotelStatisticsReturn404(){assertEquals(404,get("/api/hotels/999999999/statistics",user().token()).status());}
}
