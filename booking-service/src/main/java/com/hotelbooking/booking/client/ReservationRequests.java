package com.hotelbooking.booking.client;

import com.hotelbooking.booking.client.dto.ConfirmAvailabilityRequest;
import com.hotelbooking.booking.client.dto.ReleaseRequest;
import com.hotelbooking.booking.dto.BookingResponse;

public final class ReservationRequests {

    private static final String SEPARATOR = ":";

    private ReservationRequests() {}

    public static ConfirmAvailabilityRequest confirm(BookingResponse booking, Long roomId) {
        return new ConfirmAvailabilityRequest(
                requestId(booking, roomId), booking.id(), booking.startDate(), booking.endDate());
    }

    public static ReleaseRequest release(BookingResponse booking, Long roomId) {
        return new ReleaseRequest(requestId(booking, roomId), booking.id());
    }

    private static String requestId(BookingResponse booking, Long roomId) {
        return booking.id() + SEPARATOR + roomId;
    }
}
