package com.hotelbooking.booking.service;
import com.hotelbooking.booking.client.HotelServiceClient;
import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.dto.client.HotelRoomResponse;
import com.hotelbooking.booking.exception.*;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BookingValidationCoverageUnitTest {
 HotelServiceClient hotel=mock(HotelServiceClient.class);
 BookingRepository repo=mock(BookingRepository.class);
 BookingMapper mapper=mock(BookingMapper.class);
 BookingSagaStateService state=mock(BookingSagaStateService.class);
 BookingService service=new BookingService(hotel,repo,mapper,state,mock(Validator.class));
 UUID key=UUID.fromString("90000000-0000-0000-0000-000000000001");
 LocalDate in=LocalDate.of(2055,1,1),out=in.plusDays(2);
 BookingRequest manual(Long room,Long hotelId,LocalDate a,LocalDate b,int guests){return new BookingRequest(room,hotelId,false,a,b,guests,null);}
 BookingRequest auto(Long hotelId,LocalDate a,LocalDate b,int guests){return new BookingRequest(null,hotelId,true,a,b,guests,null);}
 @Test void checkInIsRequired(){assertThrows(IllegalArgumentException.class,()->service.createBooking(1L,manual(1L,null,null,out,1),key));}
 @Test void checkoutIsRequired(){assertThrows(IllegalArgumentException.class,()->service.createBooking(1L,manual(1L,null,in,null,1),key));}
 @Test void checkoutMustBeAfterCheckin(){assertThrows(IllegalArgumentException.class,()->service.createBooking(1L,manual(1L,null,in,in,1),key));}
 @Test void maximumStayIs365Nights(){assertThrows(IllegalArgumentException.class,()->service.createBooking(1L,manual(1L,null,in,in.plusDays(366),1),key));}
 @Test void roomIdMustBePositive(){assertThrows(IllegalArgumentException.class,()->service.createBooking(1L,manual(0L,null,in,out,1),key));}
 @Test void manualBookingRejectsHotelId(){assertThrows(IllegalArgumentException.class,()->service.createBooking(1L,manual(1L,2L,in,out,1),key));}
 @Test void autoBookingRequiresPositiveHotelId(){assertThrows(IllegalArgumentException.class,()->service.createBooking(1L,auto(0L,in,out,1),key));}
 @Test void autoBookingRejectsNoRecommendations(){when(hotel.recommendRooms(eq(2L),eq(in),eq(out),isNull(),eq(1))).thenReturn(List.of());assertThrows(NoAvailableRoomsException.class,()->service.createBooking(1L,auto(2L,in,out,1),key));}
 @Test void manualBookingRejectsUnavailableRoom(){when(hotel.getRoom(9L)).thenReturn(new HotelRoomResponse(9L,2L,"9",false,0,"STANDARD",2,BigDecimal.TEN));assertThrows(RoomUnavailableException.class,()->service.createBooking(1L,manual(9L,null,in,out,1),key));}
 @Test void manualBookingRejectsInsufficientCapacity(){when(hotel.getRoom(9L)).thenReturn(new HotelRoomResponse(9L,2L,"9",true,0,"STANDARD",2,BigDecimal.TEN));assertThrows(IllegalArgumentException.class,()->service.createBooking(1L,manual(9L,null,in,out,3),key));}
}
