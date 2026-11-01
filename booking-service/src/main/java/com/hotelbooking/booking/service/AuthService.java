package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.AuthRequest;
import com.hotelbooking.booking.dto.RegisterRequest;
import com.hotelbooking.booking.dto.TokenResponse;

public interface AuthService {

    TokenResponse register(RegisterRequest request);

    TokenResponse authenticate(AuthRequest request);
}
