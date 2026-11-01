package com.hotelbooking.hotel.controller;

import com.hotelbooking.common.security.Authorities;
import com.hotelbooking.hotel.dto.ConfirmAvailabilityRequest;
import com.hotelbooking.hotel.dto.ReleaseRequest;
import com.hotelbooking.hotel.dto.ReleaseResponse;
import com.hotelbooking.hotel.dto.ReservationResponse;
import com.hotelbooking.hotel.service.RoomAvailabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{id}")
@RequiredArgsConstructor
public class RoomAvailabilityController {

    private final RoomAvailabilityService availabilityService;

    @PostMapping("/confirm-availability")
    @PreAuthorize(Authorities.INTERNAL)
    public ReservationResponse confirm(@PathVariable Long id, @Valid @RequestBody ConfirmAvailabilityRequest request) {
        return availabilityService.confirm(id, request);
    }

    @PostMapping("/release")
    @PreAuthorize(Authorities.INTERNAL)
    public ReleaseResponse release(@PathVariable Long id, @Valid @RequestBody ReleaseRequest request) {
        return availabilityService.release(id, request);
    }
}
