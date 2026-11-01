package com.hotelbooking.booking.service.impl;

import com.hotelbooking.booking.dto.CreateUserRequest;
import com.hotelbooking.booking.dto.UpdateUserRequest;
import com.hotelbooking.booking.dto.UserResponse;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.mapper.UserMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import com.hotelbooking.booking.repository.UserRepository;
import com.hotelbooking.booking.service.UserService;
import com.hotelbooking.common.error.ConflictException;
import com.hotelbooking.common.error.NotFoundException;
import com.hotelbooking.common.web.PageResponse;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<BookingStatus> ACTIVE_STATUSES =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        requireFreeUsername(request.username());
        User user = userRepository.save(User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build());
        log.info("User {} created with role {}", user.getId(), user.getRole());
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable).map(userMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userMapper.toResponse(load(id));
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = load(id);
        applyUsername(user, request.username());
        if (request.password() != null) {
            user.changePassword(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            user.changeRole(request.role());
        }
        log.info("User {} updated", id);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = load(id);
        if (bookingRepository.existsByUserIdAndStatusIn(id, ACTIVE_STATUSES)) {
            throw new ConflictException("User has active bookings");
        }
        bookingRepository.deleteByUserId(id);
        userRepository.delete(user);
        log.info("User {} deleted", id);
    }

    private void applyUsername(User user, String username) {
        if (username == null || username.equals(user.getUsername())) {
            return;
        }
        requireFreeUsername(username);
        user.rename(username);
    }

    private void requireFreeUsername(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username is already taken");
        }
    }

    private User load(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
    }
}
