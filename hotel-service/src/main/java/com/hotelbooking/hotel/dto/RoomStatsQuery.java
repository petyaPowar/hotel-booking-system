package com.hotelbooking.hotel.dto;

import jakarta.validation.constraints.Pattern;

public record RoomStatsQuery(
        Long hotelId,
        @Pattern(regexp = "timesBooked|occupancyRate|id", message = "must be one of timesBooked, occupancyRate, id")
                String sortBy,
        @Pattern(regexp = "(?i)asc|desc", message = "must be asc or desc") String direction) {

    private static final String DEFAULT_SORT = "timesBooked";

    public String sortByOrDefault() {
        return sortBy == null ? DEFAULT_SORT : sortBy;
    }

    public boolean descending() {
        return "desc".equalsIgnoreCase(direction);
    }
}
