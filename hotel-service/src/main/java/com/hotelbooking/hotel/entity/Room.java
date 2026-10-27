package com.hotelbooking.hotel.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "rooms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rooms_hotel_number",
                columnNames = {"hotel_id", "number"}
        )
)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false, length = 20)
    private String number;

    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "times_booked", nullable = false)
    private long timesBooked = 0;

    protected Room() {
    }

    public Room(Hotel hotel, String number) {
        this.hotel = hotel;
        this.number = number;
    }

    public Long getId() {
        return id;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public String getNumber() {
        return number;
    }

    public void changeNumber(String number) {
        this.number = number;
    }

    public boolean isAvailable() {
        return available;
    }

    public long getTimesBooked() {
        return timesBooked;
    }

    public void changeAvailability(boolean available) {
        this.available = available;
    }

    public void incrementTimesBooked() {
        this.timesBooked++;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}