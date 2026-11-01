package com.hotelbooking.hotel.dto;

import com.hotelbooking.common.validation.DateRange;
import com.hotelbooking.common.validation.ValidDateRange;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@ValidDateRange
public record FreeRoomQuery(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        Long hotelId)
        implements DateRange {

    @Override
    public LocalDate rangeStart() {
        return startDate;
    }

    @Override
    public LocalDate rangeEnd() {
        return endDate;
    }
}
