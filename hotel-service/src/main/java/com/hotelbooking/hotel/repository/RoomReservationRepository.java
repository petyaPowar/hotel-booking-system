package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.RoomReservation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomReservationRepository extends JpaRepository<RoomReservation, Long> {

    Optional<RoomReservation> findByRequestId(String requestId);

    boolean existsByRequestId(String requestId);

    @Query(
            """
            select count(rr) > 0 from RoomReservation rr
            where rr.room.id = :roomId
              and rr.status = com.hotelbooking.hotel.entity.ReservationStatus.HELD
              and rr.startDate < :endDate
              and rr.endDate > :startDate
            """)
    boolean existsHeldOverlapping(
            @Param("roomId") Long roomId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    List<RoomReservation> findAllByRoomIdAndBookingIdAndStatus(Long roomId, Long bookingId, ReservationStatus status);

    boolean existsByRoomIdAndStatus(Long roomId, ReservationStatus status);

    boolean existsByRoomHotelIdAndStatus(Long hotelId, ReservationStatus status);

    @Query(
            """
            select new com.hotelbooking.hotel.repository.ReservationPeriod(rr.room.hotel.id, rr.room.id, rr.startDate, rr.endDate)
            from RoomReservation rr
            where rr.status = com.hotelbooking.hotel.entity.ReservationStatus.HELD
              and rr.startDate < :to
              and rr.endDate > :from
              and (:hotelId is null or rr.room.hotel.id = :hotelId)
            """)
    List<ReservationPeriod> findHeldPeriodsIntersecting(
            @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("hotelId") Long hotelId);

    @Modifying
    @Query("delete from RoomReservation rr where rr.room.id = :roomId")
    void deleteAllByRoomId(@Param("roomId") Long roomId);

    @Modifying
    @Query("delete from RoomReservation rr where rr.room.id in (select r.id from Room r where r.hotel.id = :hotelId)")
    void deleteAllByHotelId(@Param("hotelId") Long hotelId);
}
