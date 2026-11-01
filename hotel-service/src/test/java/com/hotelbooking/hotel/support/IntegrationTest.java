package com.hotelbooking.hotel.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.common.security.JwtProperties;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomReservation;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import java.time.LocalDate;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    protected static final LocalDate BASE_DATE = LocalDate.of(2030, 1, 10);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected HotelRepository hotelRepository;

    @Autowired
    protected RoomRepository roomRepository;

    @Autowired
    protected RoomReservationRepository reservationRepository;

    @Autowired
    private SecretKey jwtSecretKey;

    @Autowired
    private JwtProperties jwtProperties;

    protected JwtTestTokens tokens;

    @BeforeEach
    void resetState() {
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        hotelRepository.deleteAllInBatch();
        tokens = new JwtTestTokens(jwtSecretKey, jwtProperties);
    }

    protected Hotel hotel(String name, String address) {
        return hotelRepository.save(new Hotel(name, address));
    }

    protected Room room(Hotel hotel, String number, boolean available, long timesBooked) {
        return roomRepository.save(new Room(hotel, number, available, timesBooked));
    }

    protected RoomReservation heldReservation(Room room, long bookingId, LocalDate start, LocalDate end) {
        return reservationRepository.save(new RoomReservation(room, bookingId, "req-" + bookingId, start, end));
    }

    protected RoomReservation releasedReservation(Room room, long bookingId, LocalDate start, LocalDate end) {
        RoomReservation reservation = heldReservation(room, bookingId, start, end);
        reservation.release();
        return reservationRepository.save(reservation);
    }

    protected Room reload(Room room) {
        return roomRepository.findById(room.getId()).orElseThrow();
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
