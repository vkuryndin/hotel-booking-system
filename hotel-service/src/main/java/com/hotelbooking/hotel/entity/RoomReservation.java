package com.hotelbooking.hotel.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "room_reservations")
public class RoomReservation {

    @Id
    private UUID reservationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    private LocalDate checkIn;
    private LocalDate checkOut;

    // Nullable for RELEASED tombstones created before reserve().
    @Column(name = "guest_count")
    private Integer guestCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    // A release request may arrive before reserve(). Such a row exists only
    // to reject late arrivals using the same reservationId; it is not a booking.
    @Column(name = "tombstone", nullable = false)
    private boolean tombstone;

    @Version
    private Long version;

    protected RoomReservation() {
    }

    public RoomReservation(
            UUID reservationId,
            Room room,
            LocalDate checkIn,
            LocalDate checkOut,
            int guestCount
    ) {
        this.reservationId = reservationId;
        this.room = room;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.guestCount = guestCount;
        this.status = ReservationStatus.RESERVED;
    }

    public static RoomReservation released(UUID reservationId, Room room) {
        RoomReservation reservation = new RoomReservation();
        reservation.reservationId = reservationId;
        reservation.room = room;
        reservation.status = ReservationStatus.RELEASED;
        reservation.tombstone = true;
        return reservation;
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public Room getRoom() {
        return room;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public Integer getGuestCount() {
        return guestCount;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public boolean isTombstone() {
        return tombstone;
    }
}