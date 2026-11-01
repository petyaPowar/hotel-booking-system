package com.hotelbooking.booking.dto;

import com.hotelbooking.booking.entity.UserRole;
import java.time.Instant;

public record UserResponse(Long id, String username, UserRole role, Instant createdAt) {}
