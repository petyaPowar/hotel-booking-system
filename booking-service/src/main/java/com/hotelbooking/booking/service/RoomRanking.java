package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.dto.RoomResponse;
import java.util.Comparator;
import java.util.List;

public final class RoomRanking {

    private static final Comparator<RoomResponse> LEAST_BOOKED_FIRST =
            Comparator.comparingLong(RoomResponse::timesBooked).thenComparing(RoomResponse::id);

    private RoomRanking() {}

    public static List<RoomResponse> leastBookedFirst(List<RoomResponse> rooms) {
        return rooms.stream().sorted(LEAST_BOOKED_FIRST).toList();
    }
}
