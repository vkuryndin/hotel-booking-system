package com.hotelbooking.booking.repository;

import com.hotelbooking.booking.entity.BookingNight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingNightRepository
        extends JpaRepository<BookingNight, Long> {

    @Modifying
    @Query("""
            DELETE FROM BookingNight n
            WHERE n.booking.id = :bookingId
            """)
    int deleteByBookingId(@Param("bookingId") Long bookingId);
}