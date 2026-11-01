package com.hotelbooking.booking.exception;

import com.hotelbooking.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class BookingAccessDeniedException extends ApiException {

    public BookingAccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "Booking belongs to another user");
    }
}
