package com.hotelbooking.hotel.service;

import com.hotelbooking.common.web.PageResponse;
import com.hotelbooking.hotel.dto.HotelFilter;
import com.hotelbooking.hotel.dto.HotelRequest;
import com.hotelbooking.hotel.dto.HotelResponse;
import org.springframework.data.domain.Pageable;

public interface HotelService {

    HotelResponse create(HotelRequest request);

    PageResponse<HotelResponse> findAll(HotelFilter filter, Pageable pageable);

    HotelResponse findById(Long id);

    HotelResponse update(Long id, HotelRequest request);

    void delete(Long id);
}
