package com.hotelbooking.hotel.repository;

public record HotelRoomSummary(
        Long hotelId, String name, long totalRooms, long availableRooms, long totalTimesBooked) {}
