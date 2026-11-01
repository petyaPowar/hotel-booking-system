package com.hotelbooking.booking.client.dto;

import java.time.LocalDate;

public record ReservationResponse(
        Long reservationId,
        String requestId,
        Long bookingId,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        String status) {}
