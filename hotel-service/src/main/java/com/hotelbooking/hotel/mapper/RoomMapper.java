package com.hotelbooking.hotel.mapper;

import com.hotelbooking.hotel.dto.RoomResponse;
import com.hotelbooking.hotel.entity.Room;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface RoomMapper {

    @Mapping(target = "hotelId", source = "hotel.id")
    RoomResponse toResponse(Room room);

    List<RoomResponse> toResponses(List<Room> rooms);
}
