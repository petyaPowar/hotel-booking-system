package com.hotelbooking.booking.service.impl;

import com.hotelbooking.booking.dto.BookingResponse;
import com.hotelbooking.booking.dto.CreateBookingRequest;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import com.hotelbooking.booking.repository.UserRepository;
import com.hotelbooking.booking.service.BookingStateService;
import com.hotelbooking.common.error.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingStateServiceImpl implements BookingStateService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingResponse> findByRequest(Long userId, String requestId) {
        return bookingRepository.findByUserIdAndRequestId(userId, requestId).map(bookingMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getById(Long id) {
        return bookingMapper.toResponse(load(id));
    }

    @Override
    @Transactional
    public BookingResponse createPending(Long userId, Long roomId, CreateBookingRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .user(user)
                .roomId(roomId)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(BookingStatus.PENDING)
                .requestId(request.requestId())
                .build());
        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse markConfirmed(Long id, Long roomId) {
        Booking booking = load(id);
        BookingStatus previous = booking.getStatus();
        booking.confirm(roomId);
        logTransition(booking, previous);
        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse markCancelled(Long id) {
        Booking booking = load(id);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.info("Booking {} already CANCELLED", id);
            return bookingMapper.toResponse(booking);
        }
        BookingStatus previous = booking.getStatus();
        booking.cancel();
        logTransition(booking, previous);
        return bookingMapper.toResponse(booking);
    }

    private void logTransition(Booking booking, BookingStatus previous) {
        log.info(
                "Booking {} status {} -> {} (room {})",
                booking.getId(),
                previous,
                booking.getStatus(),
                booking.getRoomId());
    }

    private Booking load(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new NotFoundException("Booking not found: " + id));
    }
}
