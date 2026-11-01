package com.hotelbooking.booking.client;

import com.hotelbooking.booking.client.dto.ConfirmAvailabilityRequest;
import com.hotelbooking.booking.client.dto.ReleaseRequest;
import com.hotelbooking.booking.client.dto.ReleaseResponse;
import com.hotelbooking.booking.client.dto.ReservationResponse;
import com.hotelbooking.booking.client.dto.RoomResponse;
import java.time.LocalDate;
import java.util.List;

public interface HotelClient {

    ReservationResponse confirmAvailability(Long roomId, ConfirmAvailabilityRequest request);

    ReleaseResponse release(Long roomId, ReleaseRequest request);

    List<RoomResponse> recommend(LocalDate startDate, LocalDate endDate, Long hotelId);
}
