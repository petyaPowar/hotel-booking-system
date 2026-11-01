package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.BookingResponse;
import com.hotelbooking.booking.dto.CreateBookingRequest;
import java.util.Optional;

public interface BookingStateService {

    Optional<BookingResponse> findByRequest(Long userId, String requestId);

    BookingResponse getById(Long id);

    BookingResponse createPending(Long userId, Long roomId, CreateBookingRequest request);

    BookingResponse markConfirmed(Long id, Long roomId);

    BookingResponse markCancelled(Long id);
}
