package com.hotelbooking.booking.exception;

import com.hotelbooking.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class HotelServiceUnavailableException extends ApiException {

    public HotelServiceUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Hotel service is temporarily unavailable");
    }
}
