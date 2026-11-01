package com.hotelbooking.booking.dto;

import com.hotelbooking.common.validation.DateRange;
import com.hotelbooking.common.validation.ValidDateRange;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

@ValidDateRange
public record RecommendationRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate, @Positive Long hotelId)
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
