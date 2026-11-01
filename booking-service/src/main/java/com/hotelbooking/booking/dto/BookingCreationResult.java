package com.hotelbooking.booking.dto;

public record BookingCreationResult(BookingResponse booking, boolean created) {

    public static BookingCreationResult created(BookingResponse booking) {
        return new BookingCreationResult(booking, true);
    }

    public static BookingCreationResult replayed(BookingResponse booking) {
        return new BookingCreationResult(booking, false);
    }
}
