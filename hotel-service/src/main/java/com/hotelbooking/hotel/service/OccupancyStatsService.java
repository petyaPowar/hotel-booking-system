package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.HotelOccupancyResponse;
import com.hotelbooking.hotel.dto.RoomOccupancyResponse;
import com.hotelbooking.hotel.dto.RoomStatsQuery;
import com.hotelbooking.hotel.dto.StatsPeriod;
import java.util.List;

public interface OccupancyStatsService {

    List<HotelOccupancyResponse> hotelStats(StatsPeriod period);

    List<RoomOccupancyResponse> roomStats(StatsPeriod period, RoomStatsQuery query);
}
