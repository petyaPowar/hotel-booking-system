package com.hotelbooking.hotel.repository;

import java.time.LocalDate;

public record ReservationPeriod(Long hotelId, Long roomId, LocalDate startDate, LocalDate endDate) {}
