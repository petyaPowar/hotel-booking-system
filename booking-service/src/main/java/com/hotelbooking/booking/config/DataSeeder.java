package com.hotelbooking.booking.config;

import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.entity.UserRole;
import com.hotelbooking.booking.repository.BookingRepository;
import com.hotelbooking.booking.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        userRepository.save(user("admin", "admin123", UserRole.ADMIN));
        User user = userRepository.save(user("user", "user123", UserRole.USER));
        User alice = userRepository.save(user("alice", "alice123", UserRole.USER));
        bookingRepository.saveAll(bookings(user, alice, LocalDate.now()));
        log.info("Seeded {} users and {} bookings", userRepository.count(), bookingRepository.count());
    }

    private User user(String username, String password, UserRole role) {
        return User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role(role)
                .build();
    }

    private List<Booking> bookings(User user, User alice, LocalDate today) {
        return List.of(
                booking(user, 1L, today.plusDays(1), today.plusDays(4), BookingStatus.CONFIRMED, "seed-1"),
                booking(user, 5L, today.plusDays(2), today.plusDays(5), BookingStatus.CONFIRMED, "seed-2"),
                booking(alice, 9L, today.plusDays(10), today.plusDays(12), BookingStatus.CONFIRMED, "seed-3"),
                booking(user, 2L, today.plusDays(1), today.plusDays(3), BookingStatus.CANCELLED, "seed-4"));
    }

    private Booking booking(
            User owner, Long roomId, LocalDate start, LocalDate end, BookingStatus status, String requestId) {
        return Booking.builder()
                .user(owner)
                .roomId(roomId)
                .startDate(start)
                .endDate(end)
                .status(status)
                .requestId(requestId)
                .build();
    }
}
