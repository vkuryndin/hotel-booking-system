package com.hotelbooking.hotel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomCategory category = RoomCategory.STANDARD;

    @Column(nullable = false)
    private int capacity = 2;

    @Column(
            name = "price_per_night",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal pricePerNight = BigDecimal.ZERO;

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    @CollectionTable(
            name = "room_amenities",
            joinColumns = @JoinColumn(name = "room_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "amenity", nullable = false, length = 40)
    private Set<RoomAmenity> amenities = new LinkedHashSet<>();

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
    public void decrementTimesBooked() {
        if (timesBooked <= 0) {
            throw new IllegalStateException(
                    "timesBooked cannot be negative"
            );
        }
        timesBooked--;
    }
    public void setAvailable(boolean available) {
        this.available = available;
    }

    public RoomCategory getCategory() {
        return category;
    }

    public int getCapacity() {
        return capacity;
    }

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public Set<RoomAmenity> getAmenities() {
        return amenities;
    }

    public void changeRoomDetails(
            RoomCategory category,
            Integer capacity,
            BigDecimal pricePerNight,
            Set<RoomAmenity> amenities
    ) {
        if (category != null) {
            this.category = category;
        }

        if (capacity != null) {
            if (capacity < 1) {
                throw new IllegalArgumentException("Invalid capacity");
            }
            this.capacity = capacity;
        }

        if (pricePerNight != null) {
            if (pricePerNight.signum() < 0) {
                throw new IllegalArgumentException("Invalid price");
            }
            this.pricePerNight = pricePerNight;
        }

        if (amenities != null) {
            this.amenities.clear();
            this.amenities.addAll(amenities);
        }
    }
}