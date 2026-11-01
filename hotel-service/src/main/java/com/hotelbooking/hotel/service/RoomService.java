package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.FreeRoomQuery;
import com.hotelbooking.hotel.dto.RoomCreateRequest;
import com.hotelbooking.hotel.dto.RoomResponse;
import com.hotelbooking.hotel.dto.RoomUpdateRequest;
import java.util.List;

public interface RoomService {

    RoomResponse create(RoomCreateRequest request);

    RoomResponse findById(Long id);

    RoomResponse update(Long id, RoomUpdateRequest request);

    void delete(Long id);

    List<RoomResponse> findFree(FreeRoomQuery query);

    List<RoomResponse> recommend(FreeRoomQuery query);
}
