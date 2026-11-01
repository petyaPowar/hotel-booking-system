package com.hotelbooking.hotel.service.impl;

import com.hotelbooking.hotel.dto.StatsPeriod;
import com.hotelbooking.hotel.repository.ReservationPeriod;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

final class OccupancyMath {

    private static final int RATE_SCALE = 2;

    private OccupancyMath() {}

    static long nights(StatsPeriod period) {
        return ChronoUnit.DAYS.between(period.from(), period.to());
    }

    static long bookedNights(ReservationPeriod reservation, StatsPeriod period) {
        LocalDate start = max(reservation.startDate(), period.from());
        LocalDate end = min(reservation.endDate(), period.to());
        return Math.max(0, ChronoUnit.DAYS.between(start, end));
    }

    static double rate(long bookedNights, long capacityNights) {
        if (capacityNights == 0) {
            return 0;
        }
        return BigDecimal.valueOf(bookedNights)
                .divide(BigDecimal.valueOf(capacityNights), RATE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static LocalDate max(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private static LocalDate min(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
}
