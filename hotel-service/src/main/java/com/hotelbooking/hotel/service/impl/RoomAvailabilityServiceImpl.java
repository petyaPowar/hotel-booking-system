package com.hotelbooking.hotel.service.impl;

import com.hotelbooking.common.error.ConflictException;
import com.hotelbooking.common.error.NotFoundException;
import com.hotelbooking.common.logging.BookingMdc;
import com.hotelbooking.hotel.dto.ConfirmAvailabilityRequest;
import com.hotelbooking.hotel.dto.ReleaseRequest;
import com.hotelbooking.hotel.dto.ReleaseResponse;
import com.hotelbooking.hotel.dto.ReservationResponse;
import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomReservation;
import com.hotelbooking.hotel.mapper.ReservationMapper;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.service.RoomAvailabilityService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomAvailabilityServiceImpl implements RoomAvailabilityService {

    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;

    @Override
    @Transactional
    public ReservationResponse confirm(Long roomId, ConfirmAvailabilityRequest request) {
        try (MDC.MDCCloseable ignored = BookingMdc.put(request.bookingId())) {
            log.info("Confirm availability of room {} for request {}", roomId, request.requestId());
            Room room = lockRoom(roomId);
            RoomReservation reservation = reservationRepository
                    .findByRequestId(request.requestId())
                    .map(existing -> replay(existing, roomId, request))
                    .orElseGet(() -> hold(room, request));
            return reservationMapper.toResponse(reservation);
        }
    }

    @Override
    @Transactional
    public ReleaseResponse release(Long roomId, ReleaseRequest request) {
        try (MDC.MDCCloseable ignored = BookingMdc.put(request.bookingId())) {
            log.info("Release room {} for request {}", roomId, request.requestId());
            Room room = lockRoom(roomId);
            List<RoomReservation> held = reservationRepository.findAllByRoomIdAndBookingIdAndStatus(
                    roomId, request.bookingId(), ReservationStatus.HELD);
            held.forEach(RoomReservation::release);
            room.unregisterBookings(held.size());
            if (held.isEmpty()) {
                recordTombstone(room, request);
            }
            log.info("Released {} reservations of room {}", held.size(), roomId);
            return new ReleaseResponse(request.bookingId(), roomId, held.size());
        }
    }

    private void recordTombstone(Room room, ReleaseRequest request) {
        if (reservationRepository.existsByRequestId(request.requestId())) {
            return;
        }
        reservationRepository.save(RoomReservation.tombstone(room, request.bookingId(), request.requestId()));
        log.info("No hold found for request {}, recorded released tombstone", request.requestId());
    }

    private Room lockRoom(Long roomId) {
        Room room = roomRepository
                .findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Room " + roomId + " not found"));
        log.info("Room {} locked", roomId);
        return room;
    }

    private RoomReservation replay(RoomReservation existing, Long roomId, ConfirmAvailabilityRequest request) {
        if (existing.getStatus() != ReservationStatus.HELD) {
            log.warn("Request {} refers to a released reservation", existing.getRequestId());
            throw new ConflictException("Reservation for request " + existing.getRequestId() + " was released");
        }
        if (!matches(existing, roomId, request)) {
            log.warn("Request {} already used for another reservation", existing.getRequestId());
            throw new ConflictException("Request " + existing.getRequestId() + " belongs to another reservation");
        }
        log.info("Request {} already processed, returning reservation {}", existing.getRequestId(), existing.getId());
        return existing;
    }

    private boolean matches(RoomReservation existing, Long roomId, ConfirmAvailabilityRequest request) {
        return existing.getRoom().getId().equals(roomId)
                && existing.getBookingId().equals(request.bookingId())
                && existing.getStartDate().equals(request.startDate())
                && existing.getEndDate().equals(request.endDate());
    }

    private RoomReservation hold(Room room, ConfirmAvailabilityRequest request) {
        ensureBookable(room, request);
        RoomReservation reservation = reservationRepository.save(new RoomReservation(
                room, request.bookingId(), request.requestId(), request.startDate(), request.endDate()));
        room.registerBooking();
        log.info("Room {} held by reservation {}", room.getId(), reservation.getId());
        return reservation;
    }

    private void ensureBookable(Room room, ConfirmAvailabilityRequest request) {
        if (!room.isAvailable()) {
            log.warn("Room {} is not available", room.getId());
            throw new ConflictException("Room " + room.getId() + " is not available");
        }
        if (reservationRepository.existsHeldOverlapping(room.getId(), request.startDate(), request.endDate())) {
            log.warn("Room {} already held for overlapping dates", room.getId());
            throw new ConflictException("Room " + room.getId() + " is already booked for these dates");
        }
    }
}
