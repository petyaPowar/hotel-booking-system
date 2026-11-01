package com.hotelbooking.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoomUpdateRequest(@NotBlank @Size(max = 32) String number, @NotNull Boolean available) {}
