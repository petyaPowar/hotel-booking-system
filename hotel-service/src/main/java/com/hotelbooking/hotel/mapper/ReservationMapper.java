package com.hotelbooking.hotel.mapper;

import com.hotelbooking.hotel.dto.ReservationResponse;
import com.hotelbooking.hotel.entity.RoomReservation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ReservationMapper {

    @Mapping(target = "reservationId", source = "id")
    @Mapping(target = "roomId", source = "room.id")
    ReservationResponse toResponse(RoomReservation reservation);
}
