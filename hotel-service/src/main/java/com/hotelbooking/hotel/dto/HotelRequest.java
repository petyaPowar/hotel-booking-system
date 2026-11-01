package com.hotelbooking.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HotelRequest(@NotBlank @Size(max = 255) String name, @NotBlank @Size(max = 255) String address) {}
