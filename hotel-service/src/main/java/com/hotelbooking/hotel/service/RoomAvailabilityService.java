package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.ConfirmAvailabilityRequest;
import com.hotelbooking.hotel.dto.ReleaseRequest;
import com.hotelbooking.hotel.dto.ReleaseResponse;
import com.hotelbooking.hotel.dto.ReservationResponse;

public interface RoomAvailabilityService {

    ReservationResponse confirm(Long roomId, ConfirmAvailabilityRequest request);

    ReleaseResponse release(Long roomId, ReleaseRequest request);
}
