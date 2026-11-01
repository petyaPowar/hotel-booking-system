package com.hotelbooking.booking.dto;

import com.hotelbooking.booking.entity.UserRole;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, String username, UserRole role) {}
