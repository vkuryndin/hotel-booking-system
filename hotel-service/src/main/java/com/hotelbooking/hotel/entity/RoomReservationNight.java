package com.hotelbooking.hotel.entity;

import jakarta.persistence.*;
import java.time.LocalDate;


@Entity
@Table(
        name = "room_reservation_nights",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_room_night",
                columnNames = {"room_id", "night_date"}
        ),
        indexes = @Index(
                name = "idx_reservation_night",
                columnList = "reservation_id"
        )
)
public class RoomReservationNight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private RoomReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "night_date", nullable = false)
    private LocalDate nightDate;

    protected RoomReservationNight() {
    }

    public RoomReservationNight(
            RoomReservation reservation,
            LocalDate nightDate
    ) {
        this.reservation = reservation;
        this.room = reservation.getRoom();
        this.nightDate = nightDate;
    }
}