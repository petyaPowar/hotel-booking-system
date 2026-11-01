package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.dto.RoomResponse;
import com.hotelbooking.booking.dto.BookingResponse;
import com.hotelbooking.booking.dto.RecommendationRequest;
import com.hotelbooking.booking.security.CurrentUser;
import com.hotelbooking.common.web.PageResponse;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface BookingService {

    BookingResponse findById(Long id, CurrentUser currentUser);

    PageResponse<BookingResponse> findOwn(Long userId, Pageable pageable);

    BookingResponse cancel(Long id, CurrentUser currentUser);

    List<RoomResponse> recommend(RecommendationRequest request);
}
