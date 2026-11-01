package com.hotelbooking.booking.client.dto;

import java.time.LocalDate;

public record ConfirmAvailabilityRequest(String requestId, Long bookingId, LocalDate startDate, LocalDate endDate) {}
