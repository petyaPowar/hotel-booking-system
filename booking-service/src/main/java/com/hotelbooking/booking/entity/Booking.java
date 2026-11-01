package com.hotelbooking.booking.entity;

import com.hotelbooking.common.error.ConflictException;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "bookings",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_bookings_user_request",
                        columnNames = {"user_id", "request_id"}),
        indexes = {
            @Index(name = "idx_bookings_user_created", columnList = "user_id, created_at"),
            @Index(name = "idx_bookings_room_dates", columnList = "room_id, start_date, end_date")
        })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public void confirm(Long confirmedRoomId) {
        ensureTransition(status == BookingStatus.PENDING, BookingStatus.CONFIRMED);
        this.roomId = confirmedRoomId;
        this.status = BookingStatus.CONFIRMED;
    }

    public void cancel() {
        ensureTransition(status != BookingStatus.CANCELLED, BookingStatus.CANCELLED);
        this.status = BookingStatus.CANCELLED;
    }

    private void ensureTransition(boolean allowed, BookingStatus target) {
        if (!allowed) {
            throw new ConflictException("Booking " + id + " cannot move from " + status + " to " + target);
        }
    }
}
