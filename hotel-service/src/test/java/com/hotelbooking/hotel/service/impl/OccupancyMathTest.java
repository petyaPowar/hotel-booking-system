package com.hotelbooking.hotel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotelbooking.hotel.dto.StatsPeriod;
import com.hotelbooking.hotel.repository.ReservationPeriod;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OccupancyMathTest {

    private static final LocalDate FROM = LocalDate.of(2030, 3, 1);
    private static final StatsPeriod PERIOD = new StatsPeriod(FROM, FROM.plusDays(10));

    @Test
    void bookedNights_clipsReservationToPeriod() {
        ReservationPeriod reservation = new ReservationPeriod(1L, 1L, FROM.minusDays(3), FROM.plusDays(2));

        long nights = OccupancyMath.bookedNights(reservation, PERIOD);

        assertThat(nights).isEqualTo(2);
    }

    @Test
    void bookedNights_returnsZero_whenReservationOutsidePeriod() {
        ReservationPeriod reservation = new ReservationPeriod(1L, 1L, FROM.plusDays(10), FROM.plusDays(12));

        long nights = OccupancyMath.bookedNights(reservation, PERIOD);

        assertThat(nights).isZero();
    }

    @Test
    void rate_roundsHalfUpToTwoDecimals() {
        long capacity = 3;

        double rate = OccupancyMath.rate(2, capacity);

        assertThat(rate).isEqualTo(0.67);
    }

    @Test
    void rate_returnsZero_whenCapacityIsZero() {
        long capacity = 0;

        double rate = OccupancyMath.rate(5, capacity);

        assertThat(rate).isZero();
    }

    @Test
    void nights_countsExclusiveEndDate() {
        StatsPeriod period = new StatsPeriod(FROM, FROM.plusDays(1));

        long nights = OccupancyMath.nights(period);

        assertThat(nights).isEqualTo(1);
    }
}
