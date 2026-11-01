package com.hotelbooking.booking.repository;

import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByUserIdAndRequestId(Long userId, String requestId);

    Page<Booking> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<BookingStatus> statuses);

    @Modifying
    @Query("delete from Booking b where b.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
