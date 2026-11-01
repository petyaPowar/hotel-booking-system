package com.hotelbooking.hotel.dto;

import com.hotelbooking.common.validation.DateRange;
import com.hotelbooking.common.validation.ValidDateRange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@ValidDateRange
public record ConfirmAvailabilityRequest(
        @NotBlank @Size(max = 100) String requestId,
        @NotNull Long bookingId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate)
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
