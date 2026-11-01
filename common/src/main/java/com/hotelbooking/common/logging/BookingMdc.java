package com.hotelbooking.common.logging;

import org.slf4j.MDC;

public final class BookingMdc {

    public static final String BOOKING_ID_KEY = "bookingId";

    private BookingMdc() {}

    public static MDC.MDCCloseable put(Long bookingId) {
        return MDC.putCloseable(BOOKING_ID_KEY, String.valueOf(bookingId));
    }
}
