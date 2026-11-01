package com.hotelbooking.hotel.service.impl;

import com.hotelbooking.common.error.ConflictException;
import com.hotelbooking.common.error.NotFoundException;
import com.hotelbooking.hotel.dto.FreeRoomQuery;
import com.hotelbooking.hotel.dto.RoomCreateRequest;
import com.hotelbooking.hotel.dto.RoomResponse;
import com.hotelbooking.hotel.dto.RoomUpdateRequest;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.service.RoomService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private static final Sort RECOMMENDATION_ORDER = Sort.by(Sort.Order.asc("timesBooked"), Sort.Order.asc("id"));

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomReservationRepository reservationRepository;
    private final RoomMapper roomMapper;

    @Override
    @Transactional
    public RoomResponse create(RoomCreateRequest request) {
        Hotel hotel = hotelRepository
                .findById(request.hotelId())
                .orElseThrow(() -> new NotFoundException("Hotel " + request.hotelId() + " not found"));
        if (roomRepository.existsByHotelIdAndNumber(hotel.getId(), request.number())) {
            throw duplicateNumber(request.number());
        }
        Room room = roomRepository.save(new Room(hotel, request.number(), request.availableOrDefault(), 0));
        log.info("Room {} created in hotel {}", room.getId(), hotel.getId());
        return roomMapper.toResponse(room);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse findById(Long id) {
        return roomMapper.toResponse(getRoom(id));
    }

    @Override
    @Transactional
    public RoomResponse update(Long id, RoomUpdateRequest request) {
        Room room = getRoom(id);
        if (roomRepository.existsByHotelIdAndNumberAndIdNot(room.getHotel().getId(), request.number(), id)) {
            throw duplicateNumber(request.number());
        }
        room.update(request.number(), request.available());
        log.info("Room {} updated", id);
        return roomMapper.toResponse(room);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Room room = roomRepository.findByIdForUpdate(id).orElseThrow(() -> roomNotFound(id));
        if (reservationRepository.existsByRoomIdAndStatus(id, ReservationStatus.HELD)) {
            throw new ConflictException("Room " + id + " has active reservations");
        }
        reservationRepository.deleteAllByRoomId(id);
        roomRepository.delete(room);
        log.info("Room {} deleted", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> findFree(FreeRoomQuery query) {
        return roomMapper.toResponses(
                roomRepository.findFree(query.startDate(), query.endDate(), query.hotelId(), Sort.unsorted()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> recommend(FreeRoomQuery query) {
        return roomMapper.toResponses(
                roomRepository.findFree(query.startDate(), query.endDate(), query.hotelId(), RECOMMENDATION_ORDER));
    }

    private Room getRoom(Long id) {
        return roomRepository.findById(id).orElseThrow(() -> roomNotFound(id));
    }

    private NotFoundException roomNotFound(Long id) {
        return new NotFoundException("Room " + id + " not found");
    }

    private ConflictException duplicateNumber(String number) {
        return new ConflictException("Room number " + number + " already exists in this hotel");
    }
}
