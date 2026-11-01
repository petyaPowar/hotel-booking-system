package com.hotelbooking.hotel.mapper;

import com.hotelbooking.hotel.dto.HotelResponse;
import com.hotelbooking.hotel.entity.Hotel;
import org.mapstruct.Mapper;

@Mapper
public interface HotelMapper {

    HotelResponse toResponse(Hotel hotel);
}
