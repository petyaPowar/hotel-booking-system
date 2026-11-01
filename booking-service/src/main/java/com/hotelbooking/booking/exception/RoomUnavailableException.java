package com.hotelbooking.booking.exception;

import com.hotelbooking.common.error.ConflictException;

public class RoomUnavailableException extends ConflictException {

    public RoomUnavailableException(String message) {
        super(message);
    }
}
