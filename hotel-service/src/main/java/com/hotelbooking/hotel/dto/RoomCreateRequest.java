package com.hotelbooking.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoomCreateRequest(@NotNull Long hotelId, @NotBlank @Size(max = 32) String number, Boolean available) {

    public boolean availableOrDefault() {
        return available == null || available;
    }
}
