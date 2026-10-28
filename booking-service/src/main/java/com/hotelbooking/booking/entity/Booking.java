package com.hotelbooking.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_room_dates",
                columnList = "room_id, check_in, check_out")
},
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_booking_user_idempotency",
                        columnNames = {"user_id", "idempotency_key"}
                )
        }
)

public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 128)
    private String requestFingerprint;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reservation_id", nullable = false, unique = true, updatable = false)
    private UUID reservationId;

    @Column(name = "guest_count", nullable = false)
    private int guestCount = 1;

    public int getGuestCount() {
        return guestCount;
    }

    public void assignGuestCount(int guestCount) {
        if (guestCount < 1) {
            throw new IllegalArgumentException("Invalid guestCount");
        }
        this.guestCount = guestCount;
    }

    protected Booking() {
    }

    public Booking(Long userId, Long roomId,
                   LocalDate checkIn, LocalDate checkOut) {
        this.userId = userId;
        this.roomId = roomId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.reservationId = UUID.randomUUID();
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_outcome", nullable = false)
    private BookingCreationOutcome creationOutcome =
            BookingCreationOutcome.PROCESSING;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getRoomId() { return roomId; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public BookingStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }

    public void confirm() {
        if (status != BookingStatus.PENDING) {
            throw new IllegalStateException("Booking is not pending");
        }
        status = BookingStatus.CONFIRMED;
    }

    public void cancel() {
        if (status == BookingStatus.CANCELLED) {
            return;
        }
        status = BookingStatus.CANCELLED;
    }
    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }
    public void assignIdempotencyKey(UUID key) {
        this.idempotencyKey = Objects.requireNonNull(key);
    }
    public BookingCreationOutcome getCreationOutcome() {
        return creationOutcome;
    }

    public void markCreationOutcome(BookingCreationOutcome outcome) {
        this.creationOutcome = Objects.requireNonNull(outcome);
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void assignRequestFingerprint(String fingerprint) {
        this.requestFingerprint = Objects.requireNonNull(fingerprint);
    }

    public void switchPendingRoom(Long newRoomId) {
        if (status != BookingStatus.PENDING
                || creationOutcome != BookingCreationOutcome.PROCESSING) {
            throw new IllegalStateException(
                    "Only processing bookings can change room"
            );
        }

        this.roomId = Objects.requireNonNull(newRoomId);
    }

}