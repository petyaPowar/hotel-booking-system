package com.hotelbooking.hotel.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomReservation;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {"app.seed.enabled=true", "spring.datasource.url=jdbc:h2:mem:hotelseeddb;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class DataSeederTest {

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomReservationRepository reservationRepository;

    @Test
    void seed_createsHotelsRoomsAndHeldReservations() {
        LocalDate today = LocalDate.now();

        Room firstRoom = roomRepository.findById(1L).orElseThrow();
        RoomReservation firstReservation =
                reservationRepository.findByRequestId("1:1").orElseThrow();

        assertThat(hotelRepository.count()).isEqualTo(3);
        assertThat(roomRepository.count()).isEqualTo(9);
        assertThat(reservationRepository.count()).isEqualTo(3);
        assertThat(firstRoom.getNumber()).isEqualTo("101");
        assertThat(firstRoom.getTimesBooked()).isEqualTo(5);
        assertThat(roomRepository.findById(4L).orElseThrow().isAvailable()).isFalse();
        assertThat(firstReservation.getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(firstReservation.getStartDate()).isEqualTo(today.plusDays(1));
        assertThat(firstReservation.getEndDate()).isEqualTo(today.plusDays(4));
    }
}
