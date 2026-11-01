package com.hotelbooking.hotel.dto;

public record HotelOccupancyResponse(
        Long hotelId,
        String name,
        long totalRooms,
        long availableRooms,
        long totalTimesBooked,
        long bookedNights,
        double occupancyRate) {}
