package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.Hotel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface HotelRepository extends JpaRepository<Hotel, Long>, JpaSpecificationExecutor<Hotel> {

    @Query(
            """
            select new com.hotelbooking.hotel.repository.HotelRoomSummary(
                h.id,
                h.name,
                count(r.id),
                coalesce(sum(case when r.available = true then 1L else 0L end), 0L),
                coalesce(sum(r.timesBooked), 0L))
            from Hotel h left join Room r on r.hotel = h
            group by h.id, h.name
            """)
    List<HotelRoomSummary> summarizeRooms();
}
