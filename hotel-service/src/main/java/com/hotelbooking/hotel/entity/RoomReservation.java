package com.hotelbooking.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "room_reservations",
        indexes = {
            @Index(name = "idx_reservations_room_dates", columnList = "room_id, start_date, end_date"),
            @Index(name = "idx_reservations_booking", columnList = "booking_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "request_id", nullable = false, unique = true, length = 100)
    private String requestId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    public RoomReservation(Room room, Long bookingId, String requestId, LocalDate startDate, LocalDate endDate) {
        this.room = room;
        this.bookingId = bookingId;
        this.requestId = requestId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = ReservationStatus.HELD;
        this.createdAt = Instant.now();
    }

    public static RoomReservation tombstone(Room room, Long bookingId, String requestId) {
        LocalDate today = LocalDate.now();
        RoomReservation reservation = new RoomReservation(room, bookingId, requestId, today, today);
        reservation.release();
        return reservation;
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
        this.releasedAt = Instant.now();
    }
}
