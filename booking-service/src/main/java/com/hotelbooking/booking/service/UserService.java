package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.CreateUserRequest;
import com.hotelbooking.booking.dto.UpdateUserRequest;
import com.hotelbooking.booking.dto.UserResponse;
import com.hotelbooking.common.web.PageResponse;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse create(CreateUserRequest request);

    PageResponse<UserResponse> findAll(Pageable pageable);

    UserResponse findById(Long id);

    UserResponse update(Long id, UpdateUserRequest request);

    void delete(Long id);
}
