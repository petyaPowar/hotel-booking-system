package com.hotelbooking.hotel.config;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomReservation;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hotelRepository.count() > 0) {
            return;
        }
        List<Room> rooms = seedRooms(seedHotels());
        seedReservations(rooms, LocalDate.now());
        log.info("Seeded hotels, rooms and reservations");
    }

    private List<Hotel> seedHotels() {
        return hotelRepository.saveAll(List.of(
                new Hotel("Grand Plaza", "Moscow, Tverskaya st. 1"),
                new Hotel("Sea Breeze", "Sochi, Kurortny ave. 12"),
                new Hotel("Neva View", "Saint Petersburg, Nevsky ave. 30")));
    }

    private List<Room> seedRooms(List<Hotel> hotels) {
        return roomRepository.saveAll(List.of(
                new Room(hotels.get(0), "101", true, 5),
                new Room(hotels.get(0), "102", true, 2),
                new Room(hotels.get(0), "103", true, 0),
                new Room(hotels.get(0), "104", false, 1),
                new Room(hotels.get(1), "201", true, 3),
                new Room(hotels.get(1), "202", true, 0),
                new Room(hotels.get(1), "203", true, 7),
                new Room(hotels.get(2), "301", true, 1),
                new Room(hotels.get(2), "302", true, 4)));
    }

    private void seedReservations(List<Room> rooms, LocalDate today) {
        reservationRepository.saveAll(List.of(
                new RoomReservation(rooms.get(0), 1L, "1:1", today.plusDays(1), today.plusDays(4)),
                new RoomReservation(rooms.get(4), 2L, "2:5", today.plusDays(2), today.plusDays(5)),
                new RoomReservation(rooms.get(8), 3L, "3:9", today.plusDays(10), today.plusDays(12))));
    }
}
