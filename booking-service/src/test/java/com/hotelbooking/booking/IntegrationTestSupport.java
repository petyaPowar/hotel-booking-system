package com.hotelbooking.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.entity.UserRole;
import com.hotelbooking.booking.repository.BookingRepository;
import com.hotelbooking.booking.repository.UserRepository;
import com.hotelbooking.booking.security.JwtTokenIssuer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    protected static final WireMockServer HOTEL_SERVICE =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    protected static final String DEFAULT_PASSWORD = "password123";
    protected static final LocalDate START_DATE = LocalDate.now().plusDays(5);
    protected static final LocalDate END_DATE = START_DATE.plusDays(3);

    static {
        HOTEL_SERVICE.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected BookingRepository bookingRepository;

    @Autowired
    private JwtTokenIssuer tokenIssuer;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    protected CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void hotelServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.hotel-service[0].uri", HOTEL_SERVICE::baseUrl);
    }

    @BeforeEach
    void resetState() {
        HOTEL_SERVICE.resetAll();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        bookingRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    protected User saveUser(String username, UserRole role) {
        return userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .role(role)
                .build());
    }

    protected String bearer(User user) {
        return "Bearer " + tokenIssuer.issueUserToken(user.getId(), user.getUsername(), user.getRole());
    }

    protected Booking saveBooking(User owner, Long roomId, BookingStatus status, String requestId) {
        return bookingRepository.save(Booking.builder()
                .user(owner)
                .roomId(roomId)
                .startDate(START_DATE)
                .endDate(END_DATE)
                .status(status)
                .requestId(requestId)
                .build());
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
