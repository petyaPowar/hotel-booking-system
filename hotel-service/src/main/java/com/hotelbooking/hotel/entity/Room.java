package com.hotelbooking.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "rooms",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_rooms_hotel_number",
                        columnNames = {"hotel_id", "number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "times_booked", nullable = false)
    private long timesBooked;

    @Version
    private Long version;

    public Room(Hotel hotel, String number, boolean available, long timesBooked) {
        this.hotel = hotel;
        this.number = number;
        this.available = available;
        this.timesBooked = timesBooked;
    }

    public void update(String number, boolean available) {
        this.number = number;
        this.available = available;
    }

    public void registerBooking() {
        timesBooked++;
    }

    public void unregisterBookings(int count) {
        timesBooked = Math.max(0, timesBooked - count);
    }
}
