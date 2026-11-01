package com.hotelbooking.hotel.dto;

import com.hotelbooking.common.validation.DateRange;
import com.hotelbooking.common.validation.ValidDateRange;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@ValidDateRange(field = "to")
public record StatsPeriod(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)
        implements DateRange {

    @Override
    public LocalDate rangeStart() {
        return from;
    }

    @Override
    public LocalDate rangeEnd() {
        return to;
    }
}
