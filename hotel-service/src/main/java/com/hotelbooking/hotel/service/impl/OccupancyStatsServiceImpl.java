package com.hotelbooking.hotel.service.impl;

import com.hotelbooking.hotel.dto.HotelOccupancyResponse;
import com.hotelbooking.hotel.dto.RoomOccupancyResponse;
import com.hotelbooking.hotel.dto.RoomStatsQuery;
import com.hotelbooking.hotel.dto.StatsPeriod;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.HotelRoomSummary;
import com.hotelbooking.hotel.repository.ReservationPeriod;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.service.OccupancyStatsService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OccupancyStatsServiceImpl implements OccupancyStatsService {

    private static final Comparator<HotelOccupancyResponse> HOTEL_ORDER = Comparator.comparingDouble(
                    HotelOccupancyResponse::occupancyRate)
            .reversed()
            .thenComparing(HotelOccupancyResponse::hotelId);

    private static final Map<String, Comparator<RoomOccupancyResponse>> ROOM_ORDERS = Map.of(
            "timesBooked", Comparator.comparingLong(RoomOccupancyResponse::timesBooked),
            "occupancyRate", Comparator.comparingDouble(RoomOccupancyResponse::occupancyRate),
            "id", Comparator.comparing(RoomOccupancyResponse::roomId));

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<HotelOccupancyResponse> hotelStats(StatsPeriod period) {
        Map<Long, Long> nightsByHotel = sumNights(heldPeriods(period, null), period, ReservationPeriod::hotelId);
        long periodNights = OccupancyMath.nights(period);
        return hotelRepository.summarizeRooms().stream()
                .map(summary ->
                        toHotelOccupancy(summary, nightsByHotel.getOrDefault(summary.hotelId(), 0L), periodNights))
                .sorted(HOTEL_ORDER)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomOccupancyResponse> roomStats(StatsPeriod period, RoomStatsQuery query) {
        List<ReservationPeriod> periods = heldPeriods(period, query.hotelId());
        Map<Long, Long> nightsByRoom = sumNights(periods, period, ReservationPeriod::roomId);
        Map<Long, Long> activeByRoom =
                periods.stream().collect(Collectors.groupingBy(ReservationPeriod::roomId, Collectors.counting()));
        long periodNights = OccupancyMath.nights(period);
        return roomRepository.findAllByOptionalHotelId(query.hotelId()).stream()
                .map(room -> toRoomOccupancy(
                        room,
                        activeByRoom.getOrDefault(room.getId(), 0L),
                        nightsByRoom.getOrDefault(room.getId(), 0L),
                        periodNights))
                .sorted(roomOrder(query))
                .toList();
    }

    private List<ReservationPeriod> heldPeriods(StatsPeriod period, Long hotelId) {
        return reservationRepository.findHeldPeriodsIntersecting(period.from(), period.to(), hotelId);
    }

    private Map<Long, Long> sumNights(
            List<ReservationPeriod> periods, StatsPeriod period, Function<ReservationPeriod, Long> key) {
        return periods.stream()
                .collect(Collectors.groupingBy(
                        key, Collectors.summingLong(reservation -> OccupancyMath.bookedNights(reservation, period))));
    }

    private HotelOccupancyResponse toHotelOccupancy(HotelRoomSummary summary, long bookedNights, long periodNights) {
        return new HotelOccupancyResponse(
                summary.hotelId(),
                summary.name(),
                summary.totalRooms(),
                summary.availableRooms(),
                summary.totalTimesBooked(),
                bookedNights,
                OccupancyMath.rate(bookedNights, summary.totalRooms() * periodNights));
    }

    private RoomOccupancyResponse toRoomOccupancy(
            Room room, long activeReservations, long bookedNights, long periodNights) {
        return new RoomOccupancyResponse(
                room.getId(),
                room.getHotel().getId(),
                room.getNumber(),
                room.isAvailable(),
                room.getTimesBooked(),
                activeReservations,
                bookedNights,
                OccupancyMath.rate(bookedNights, periodNights));
    }

    private Comparator<RoomOccupancyResponse> roomOrder(RoomStatsQuery query) {
        Comparator<RoomOccupancyResponse> order = ROOM_ORDERS.get(query.sortByOrDefault());
        Comparator<RoomOccupancyResponse> directed = query.descending() ? order.reversed() : order;
        return directed.thenComparing(RoomOccupancyResponse::roomId);
    }
}
