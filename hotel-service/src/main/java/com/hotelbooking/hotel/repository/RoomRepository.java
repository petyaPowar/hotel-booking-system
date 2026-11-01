package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.Room;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.hotel.id = :hotelId")
    List<Room> findAllByHotelIdForUpdate(@Param("hotelId") Long hotelId);

    @Query(
            """
            select r from Room r
            where r.available = true
              and (:hotelId is null or r.hotel.id = :hotelId)
              and not exists (
                select rr.id from RoomReservation rr
                where rr.room = r
                  and rr.status = com.hotelbooking.hotel.entity.ReservationStatus.HELD
                  and rr.startDate < :endDate
                  and rr.endDate > :startDate)
            """)
    List<Room> findFree(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("hotelId") Long hotelId,
            Sort sort);

    @Query("select r from Room r where :hotelId is null or r.hotel.id = :hotelId")
    List<Room> findAllByOptionalHotelId(@Param("hotelId") Long hotelId);

    boolean existsByHotelIdAndNumber(Long hotelId, String number);

    boolean existsByHotelIdAndNumberAndIdNot(Long hotelId, String number, Long id);
}
