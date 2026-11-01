package com.hotelbooking.booking.dto;

import com.hotelbooking.booking.entity.UserRole;
import com.hotelbooking.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 6, max = 72) @MaxUtf8Bytes(72) String password,
        @NotNull UserRole role) {}
