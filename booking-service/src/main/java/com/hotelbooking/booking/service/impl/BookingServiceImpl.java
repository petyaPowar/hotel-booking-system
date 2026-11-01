package com.hotelbooking.booking.service.impl;

import com.hotelbooking.booking.client.HotelClient;
import com.hotelbooking.booking.client.ReservationRequests;
import com.hotelbooking.booking.client.dto.ReleaseResponse;
import com.hotelbooking.booking.client.dto.RoomResponse;
import com.hotelbooking.booking.dto.BookingResponse;
import com.hotelbooking.booking.dto.RecommendationRequest;
import com.hotelbooking.booking.exception.BookingAccessDeniedException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import com.hotelbooking.booking.security.CurrentUser;
import com.hotelbooking.booking.service.BookingService;
import com.hotelbooking.booking.service.BookingStateService;
import com.hotelbooking.booking.service.RoomRanking;
import com.hotelbooking.common.error.ConflictException;
import com.hotelbooking.common.logging.BookingMdc;
import com.hotelbooking.common.web.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingStateService stateService;
    private final BookingMapper bookingMapper;
    private final HotelClient hotelClient;

    @Override
    public BookingResponse findById(Long id, CurrentUser currentUser) {
        BookingResponse booking = stateService.getById(id);
        if (!currentUser.canAccess(booking.userId())) {
            throw new BookingAccessDeniedException();
        }
        return booking;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> findOwn(Long userId, Pageable pageable) {
        return PageResponse.from(
                bookingRepository.findByUserId(userId, pageable).map(bookingMapper::toResponse));
    }

    @Override
    public BookingResponse cancel(Long id, CurrentUser currentUser) {
        BookingResponse booking = findById(id, currentUser);
        return switch (booking.status()) {
            case CANCELLED -> booking;
            case PENDING -> cancelPending(booking, currentUser);
            case CONFIRMED -> releaseAndCancel(booking);
        };
    }

    @Override
    public List<RoomResponse> recommend(RecommendationRequest request) {
        return RoomRanking.leastBookedFirst(
                hotelClient.recommend(request.startDate(), request.endDate(), request.hotelId()));
    }

    private BookingResponse cancelPending(BookingResponse booking, CurrentUser currentUser) {
        if (!currentUser.admin()) {
            throw new ConflictException("Booking is still being processed");
        }
        log.warn("Admin {} cancels PENDING booking {}", currentUser.id(), booking.id());
        return releaseAndCancel(booking);
    }

    private BookingResponse releaseAndCancel(BookingResponse booking) {
        try (MDC.MDCCloseable ignored = BookingMdc.put(booking.id())) {
            ReleaseResponse released =
                    hotelClient.release(booking.roomId(), ReservationRequests.release(booking, booking.roomId()));
            log.info("Released {} reservation(s) on room {}", released.releasedCount(), booking.roomId());
            return stateService.markCancelled(booking.id());
        }
    }
}
