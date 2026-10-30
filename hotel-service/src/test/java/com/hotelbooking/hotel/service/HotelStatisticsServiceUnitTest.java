package com.hotelbooking.hotel.service;
import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class HotelStatisticsServiceUnitTest {
 HotelRepository hotels; RoomRepository rooms; RoomReservationRepository reservations; RoomReservationNightRepository nights; HotelStatisticsService service;
 @BeforeEach void init(){hotels=mock(HotelRepository.class);rooms=mock(RoomRepository.class);reservations=mock(RoomReservationRepository.class);nights=mock(RoomReservationNightRepository.class);service=new HotelStatisticsService(hotels,rooms,reservations,nights);}
 @Test void missingHotelFailsBeforeAggregation(){assertThrows(HotelNotFoundException.class,()->service.getStatistics(2L));verifyNoInteractions(rooms,reservations,nights);}
 @Test void aggregatesAllCountersAndExcludesTombstones(){when(hotels.existsById(2L)).thenReturn(true);when(rooms.countByHotelId(2L)).thenReturn(10L);when(rooms.countByHotelIdAndAvailableTrue(2L)).thenReturn(7L);when(nights.countOccupiedRoomsOnDate(eq(2L),any(LocalDate.class))).thenReturn(4L);when(reservations.countByRoom_Hotel_IdAndTombstoneFalse(2L)).thenReturn(8L);when(reservations.countByRoom_Hotel_IdAndStatusAndTombstoneFalse(2L,ReservationStatus.RESERVED)).thenReturn(6L);when(reservations.countByRoom_Hotel_IdAndStatusAndTombstoneFalse(2L,ReservationStatus.RELEASED)).thenReturn(2L);when(reservations.countByRoom_Hotel_IdAndTombstoneTrue(2L)).thenReturn(3L);when(rooms.sumTimesBookedByHotelId(2L)).thenReturn(6L);var r=service.getStatistics(2L);assertEquals(2L,r.hotelId());assertEquals(LocalDate.now(),r.statisticsDate());assertEquals(10,r.totalRooms());assertEquals(7,r.availableRooms());assertEquals(3,r.unavailableRooms());assertEquals(4,r.occupiedRoomsToday());assertEquals(8,r.totalReservations());assertEquals(6,r.reservedReservations());assertEquals(2,r.releasedReservations());assertEquals(6,r.totalTimesBooked());assertEquals(3,r.tombstoneReservations());}
 @Test void nullSumIsZero(){when(hotels.existsById(2L)).thenReturn(true);var r=service.getStatistics(2L);assertEquals(0L,r.totalTimesBooked());}
}
