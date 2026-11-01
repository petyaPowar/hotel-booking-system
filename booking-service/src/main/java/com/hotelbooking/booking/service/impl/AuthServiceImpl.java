package com.hotelbooking.booking.service.impl;

import com.hotelbooking.booking.dto.AuthRequest;
import com.hotelbooking.booking.dto.CreateUserRequest;
import com.hotelbooking.booking.dto.RegisterRequest;
import com.hotelbooking.booking.dto.TokenResponse;
import com.hotelbooking.booking.dto.UserResponse;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.entity.UserRole;
import com.hotelbooking.booking.exception.InvalidCredentialsException;
import com.hotelbooking.booking.repository.UserRepository;
import com.hotelbooking.booking.security.JwtTokenIssuer;
import com.hotelbooking.booking.service.AuthService;
import com.hotelbooking.booking.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenIssuer tokenIssuer;

    @Override
    public TokenResponse register(RegisterRequest request) {
        UserResponse user =
                userService.create(new CreateUserRequest(request.username(), request.password(), UserRole.USER));
        log.info("User {} registered", user.id());
        return token(user.id(), user.username(), user.role());
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse authenticate(AuthRequest request) {
        User user = userRepository
                .findByUsername(request.username())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
                .orElseThrow(InvalidCredentialsException::new);
        return token(user.getId(), user.getUsername(), user.getRole());
    }

    private TokenResponse token(Long userId, String username, UserRole role) {
        String accessToken = tokenIssuer.issueUserToken(userId, username, role);
        return new TokenResponse(accessToken, TOKEN_TYPE, tokenIssuer.tokenTtl().toSeconds(), username, role);
    }
}
