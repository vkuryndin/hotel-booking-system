package com.hotelbooking.hotel.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_hotel_name_address",
                        columnNames = {"name", "address"}
                ),
                @UniqueConstraint(
                        name = "uk_hotel_normalized_name_address",
                        columnNames = {
                                "normalized_name",
                                "normalized_address"
                        }
                )
        }
)
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private String address;

    // Нормализованные значения для уникального ограничения БД.
    @Column(name = "normalized_name", nullable = false, length = 150)
    private String normalizedName;

    @Column(name = "normalized_address", nullable = false, length = 255)
    private String normalizedAddress;

    @Column(length = 3000)
    private String description;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "check_in_time", nullable = false)
    private LocalTime checkInTime = LocalTime.of(14, 0);

    @Column(name = "check_out_time", nullable = false)
    private LocalTime checkOutTime = LocalTime.of(12, 0);

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    @CollectionTable(
            name = "hotel_amenities",
            joinColumns = @JoinColumn(name = "hotel_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "amenity", nullable = false, length = 40)
    private Set<HotelAmenity> amenities = new LinkedHashSet<>();

    protected Hotel() {
    }

    public Hotel(String name, String address) {
        this.name = name;
        this.address = address;
    }

    // JPA автоматически обновляет нормализованные поля
    // перед INSERT и UPDATE.
    @PrePersist
    @PreUpdate
    private void updateNormalizedFields() {
        normalizedName = name.trim().toLowerCase(Locale.ROOT);
        normalizedAddress = address.trim().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public void updateDetails(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public LocalTime getCheckInTime() {
        return checkInTime;
    }

    public LocalTime getCheckOutTime() {
        return checkOutTime;
    }

    public Set<HotelAmenity> getAmenities() {
        return amenities;
    }

    public void changePresentation(
            String description,
            BigDecimal rating,
            Set<HotelAmenity> amenities,
            LocalTime checkInTime,
            LocalTime checkOutTime
    ) {
        if (description != null) {
            this.description = description.trim();
        }

        if (rating != null) {
            this.rating = rating;
        }

        if (amenities != null) {
            this.amenities.clear();
            this.amenities.addAll(amenities);
        }

        if (checkInTime != null) {
            this.checkInTime = checkInTime;
        }

        if (checkOutTime != null) {
            this.checkOutTime = checkOutTime;
        }
    }
}