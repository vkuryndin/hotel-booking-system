package com.hotelbooking.booking.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
        name = "booking_nights",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booking_night_room_date",
                columnNames = {"room_id", "night_date"}
        ),
        indexes = @Index(
                name = "idx_booking_night_booking",
                columnList = "booking_id"
        )
)
public class BookingNight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "night_date", nullable = false)
    private LocalDate nightDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    protected BookingNight() {
    }

    public BookingNight(Booking booking, LocalDate nightDate) {
        this.booking = booking;
        this.roomId = booking.getRoomId();
        this.nightDate = nightDate;
    }

    public Long getId() {
        return id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public LocalDate getNightDate() {
        return nightDate;
    }

    public Booking getBooking() {
        return booking;
    }
}