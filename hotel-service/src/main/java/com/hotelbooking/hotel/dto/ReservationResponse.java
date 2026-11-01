package com.hotelbooking.hotel.dto;

import com.hotelbooking.hotel.entity.ReservationStatus;
import java.time.LocalDate;

public record ReservationResponse(
        Long reservationId,
        String requestId,
        Long bookingId,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        ReservationStatus status) {}
