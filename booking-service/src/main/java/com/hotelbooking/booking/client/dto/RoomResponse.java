package com.hotelbooking.booking.client.dto;

public record RoomResponse(Long id, Long hotelId, String number, boolean available, long timesBooked) {}
