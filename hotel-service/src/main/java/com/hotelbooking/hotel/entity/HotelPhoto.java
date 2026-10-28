package com.hotelbooking.hotel.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "hotel_photos",
        indexes = @Index(
                name = "idx_photo_hotel_order",
                columnList = "hotel_id, sort_order"
        )
)
public class HotelPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(name = "file_name", nullable = false, length = 64)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryPhoto;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected HotelPhoto() {
    }

    public HotelPhoto(
            Hotel hotel,
            String fileName,
            String contentType,
            boolean primaryPhoto,
            int sortOrder
    ) {
        this.hotel = hotel;
        this.fileName = fileName;
        this.contentType = contentType;
        this.primaryPhoto = primaryPhoto;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isPrimaryPhoto() {
        return primaryPhoto;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setPrimaryPhoto(boolean value) {
        this.primaryPhoto = value;
    }

    public void setSortOrder(int value) {
        this.sortOrder = value;
    }
}