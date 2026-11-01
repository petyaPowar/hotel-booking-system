package com.hotelbooking.hotel.service.impl;

import com.hotelbooking.common.error.ConflictException;
import com.hotelbooking.common.error.NotFoundException;
import com.hotelbooking.common.web.PageResponse;
import com.hotelbooking.hotel.dto.HotelFilter;
import com.hotelbooking.hotel.dto.HotelRequest;
import com.hotelbooking.hotel.dto.HotelResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.mapper.HotelMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.HotelSpecifications;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.service.HotelService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;
    private final HotelMapper hotelMapper;

    @Override
    @Transactional
    public HotelResponse create(HotelRequest request) {
        Hotel hotel = hotelRepository.save(new Hotel(request.name(), request.address()));
        log.info("Hotel {} created", hotel.getId());
        return hotelMapper.toResponse(hotel);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HotelResponse> findAll(HotelFilter filter, Pageable pageable) {
        return PageResponse.from(hotelRepository
                .findAll(HotelSpecifications.matching(filter), pageable)
                .map(hotelMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public HotelResponse findById(Long id) {
        return hotelMapper.toResponse(getHotel(id));
    }

    @Override
    @Transactional
    public HotelResponse update(Long id, HotelRequest request) {
        Hotel hotel = getHotel(id);
        hotel.update(request.name(), request.address());
        log.info("Hotel {} updated", id);
        return hotelMapper.toResponse(hotel);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Hotel hotel = getHotel(id);
        List<Room> rooms = roomRepository.findAllByHotelIdForUpdate(id);
        if (reservationRepository.existsByRoomHotelIdAndStatus(id, ReservationStatus.HELD)) {
            throw new ConflictException("Hotel " + id + " has active reservations");
        }
        reservationRepository.deleteAllByHotelId(id);
        roomRepository.deleteAll(rooms);
        hotelRepository.delete(hotel);
        log.info("Hotel {} deleted", id);
    }

    private Hotel getHotel(Long id) {
        return hotelRepository.findById(id).orElseThrow(() -> new NotFoundException("Hotel " + id + " not found"));
    }
}
