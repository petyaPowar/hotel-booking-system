package com.hotelbooking.hotel.dto;

public record RoomOccupancyResponse(
        Long roomId,
        Long hotelId,
        String number,
        boolean available,
        long timesBooked,
        long activeReservations,
        long bookedNights,
        double occupancyRate) {}
