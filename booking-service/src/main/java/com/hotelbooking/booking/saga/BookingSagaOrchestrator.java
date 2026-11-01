package com.hotelbooking.booking.saga;

import com.hotelbooking.booking.client.HotelClient;
import com.hotelbooking.booking.client.ReservationRequests;
import com.hotelbooking.booking.client.dto.ReleaseResponse;
import com.hotelbooking.booking.client.dto.RoomResponse;
import com.hotelbooking.booking.dto.BookingCreationResult;
import com.hotelbooking.booking.dto.BookingResponse;
import com.hotelbooking.booking.dto.CreateBookingRequest;
import com.hotelbooking.booking.exception.HotelServiceUnavailableException;
import com.hotelbooking.booking.exception.RoomUnavailableException;
import com.hotelbooking.booking.service.BookingCreationService;
import com.hotelbooking.booking.service.BookingStateService;
import com.hotelbooking.booking.service.RoomRanking;
import com.hotelbooking.common.error.ApiException;
import com.hotelbooking.common.error.ConflictException;
import com.hotelbooking.common.error.NotFoundException;
import com.hotelbooking.common.logging.BookingMdc;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingSagaOrchestrator implements BookingCreationService {

    private final BookingStateService stateService;
    private final HotelClient hotelClient;

    @Override
    public BookingCreationResult create(Long userId, CreateBookingRequest request) {
        return stateService
                .findByRequest(userId, request.requestId())
                .map(existing -> replay(existing, request))
                .orElseGet(() -> start(userId, request));
    }

    private BookingCreationResult replay(BookingResponse existing, CreateBookingRequest request) {
        if (!sameParameters(existing, request)) {
            log.warn("Request {} reused with different parameters", request.requestId());
            throw new ConflictException("requestId already used with different parameters");
        }
        return BookingCreationResult.replayed(existing);
    }

    private boolean sameParameters(BookingResponse existing, CreateBookingRequest request) {
        boolean sameDates = existing.startDate().equals(request.startDate())
                && existing.endDate().equals(request.endDate());
        return sameDates && (request.autoSelect() || Objects.equals(existing.roomId(), request.roomId()));
    }

    private BookingCreationResult start(Long userId, CreateBookingRequest request) {
        List<Long> candidates = resolveCandidates(request);
        BookingResponse pending;
        try {
            pending = stateService.createPending(userId, candidates.get(0), request);
        } catch (DataIntegrityViolationException ex) {
            return replayConcurrentDuplicate(userId, request, ex);
        }
        try (MDC.MDCCloseable ignored = BookingMdc.put(pending.id())) {
            log.info("Booking saga started: status PENDING, candidate rooms {}", candidates);
            return BookingCreationResult.created(confirmFirstAvailable(pending, candidates, request.autoSelect()));
        }
    }

    private BookingCreationResult replayConcurrentDuplicate(
            Long userId, CreateBookingRequest request, DataIntegrityViolationException ex) {
        log.info("Concurrent duplicate of request {} detected, returning existing booking", request.requestId());
        return stateService
                .findByRequest(userId, request.requestId())
                .map(existing -> replay(existing, request))
                .orElseThrow(() -> ex);
    }

    private List<Long> resolveCandidates(CreateBookingRequest request) {
        if (!request.autoSelect()) {
            return List.of(request.roomId());
        }
        List<Long> roomIds =
                RoomRanking.leastBookedFirst(hotelClient.recommend(request.startDate(), request.endDate(), null))
                        .stream()
                        .map(RoomResponse::id)
                        .toList();
        if (roomIds.isEmpty()) {
            throw new RoomUnavailableException("No free rooms for the requested dates");
        }
        return roomIds;
    }

    private BookingResponse confirmFirstAvailable(
            BookingResponse pending, List<Long> candidates, boolean skipMissingRooms) {
        for (Long roomId : candidates) {
            if (holdRoom(pending, roomId, skipMissingRooms)) {
                return confirmLocally(pending, roomId);
            }
        }
        stateService.markCancelled(pending.id());
        throw new RoomUnavailableException("Room is not available for the requested dates");
    }

    private boolean holdRoom(BookingResponse pending, Long roomId, boolean skipMissingRooms) {
        try {
            hotelClient.confirmAvailability(roomId, ReservationRequests.confirm(pending, roomId));
            return true;
        } catch (RoomUnavailableException ex) {
            log.info("Room {} rejected by hotel service: {}", roomId, ex.getMessage());
            return false;
        } catch (NotFoundException ex) {
            return skipMissingRoom(pending, roomId, skipMissingRooms, ex);
        } catch (HotelServiceUnavailableException ex) {
            compensate(pending, roomId);
            throw ex;
        } catch (ApiException ex) {
            stateService.markCancelled(pending.id());
            throw ex;
        }
    }

    private boolean skipMissingRoom(
            BookingResponse pending, Long roomId, boolean skipMissingRooms, NotFoundException ex) {
        if (!skipMissingRooms) {
            stateService.markCancelled(pending.id());
            throw ex;
        }
        log.info("Room {} not found in hotel service, trying next candidate", roomId);
        return false;
    }

    private BookingResponse confirmLocally(BookingResponse pending, Long roomId) {
        try {
            return stateService.markConfirmed(pending.id(), roomId);
        } catch (RuntimeException ex) {
            log.error("Failed to confirm booking {} after room {} was held", pending.id(), roomId, ex);
            compensate(pending, roomId);
            throw ex;
        }
    }

    private void compensate(BookingResponse pending, Long roomId) {
        log.warn("Starting compensation for booking {} on room {}", pending.id(), roomId);
        boolean released = releaseRoom(pending, roomId);
        stateService.markCancelled(pending.id());
        if (released) {
            log.info("Booking status PENDING -> CANCELLED (compensated)");
        } else {
            log.error("Booking status PENDING -> CANCELLED (compensation failed for room {})", roomId);
        }
    }

    private boolean releaseRoom(BookingResponse pending, Long roomId) {
        try {
            ReleaseResponse released = hotelClient.release(roomId, ReservationRequests.release(pending, roomId));
            log.info("Compensation succeeded: released {} reservation(s) on room {}", released.releasedCount(), roomId);
            return true;
        } catch (ApiException ex) {
            log.error("Compensation failed for booking {} on room {}: {}", pending.id(), roomId, ex.getMessage());
            return false;
        }
    }
}
