package com.hotelbooking.hotel.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "room_photos",
        indexes = @Index(
                name = "idx_photo_room_order",
                columnList = "room_id, sort_order"
        )
)
public class RoomPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "file_name", nullable = false, length = 64)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryPhoto;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected RoomPhoto() {
    }

    public RoomPhoto(
            Room room,
            String fileName,
            String contentType,
            boolean primaryPhoto,
            int sortOrder
    ) {
        this.room = room;
        this.fileName = fileName;
        this.contentType = contentType;
        this.primaryPhoto = primaryPhoto;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
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