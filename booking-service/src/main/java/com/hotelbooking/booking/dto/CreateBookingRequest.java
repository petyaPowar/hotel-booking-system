package com.hotelbooking.booking.dto;

import com.hotelbooking.booking.validation.ValidRoomSelection;
import com.hotelbooking.common.validation.DateRange;
import com.hotelbooking.common.validation.ValidDateRange;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@ValidDateRange
@ValidRoomSelection
public record CreateBookingRequest(
        @NotBlank @Size(max = 64) String requestId,
        @Positive Long roomId,
        boolean autoSelect,
        @NotNull @FutureOrPresent LocalDate startDate,
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
