package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.BookingCreationResult;
import com.hotelbooking.booking.dto.CreateBookingRequest;

public interface BookingCreationService {

    BookingCreationResult create(Long userId, CreateBookingRequest request);
}
