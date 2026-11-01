package com.hotelbooking.hotel.controller;

import com.hotelbooking.common.security.Authorities;
import com.hotelbooking.common.web.PageResponse;
import com.hotelbooking.hotel.dto.HotelFilter;
import com.hotelbooking.hotel.dto.HotelOccupancyResponse;
import com.hotelbooking.hotel.dto.HotelRequest;
import com.hotelbooking.hotel.dto.HotelResponse;
import com.hotelbooking.hotel.dto.StatsPeriod;
import com.hotelbooking.hotel.service.HotelService;
import com.hotelbooking.hotel.service.OccupancyStatsService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;
    private final OccupancyStatsService statsService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Authorities.ADMIN)
    public HotelResponse create(@Valid @RequestBody HotelRequest request) {
        return hotelService.create(request);
    }

    @GetMapping
    @PreAuthorize(Authorities.USER_OR_ADMIN)
    public PageResponse<HotelResponse> findAll(HotelFilter filter, @PageableDefault(sort = "id") Pageable pageable) {
        return hotelService.findAll(filter, pageable);
    }

    @GetMapping("/stats")
    @PreAuthorize(Authorities.ADMIN)
    public List<HotelOccupancyResponse> stats(@Valid StatsPeriod period) {
        return statsService.hotelStats(period);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorities.USER_OR_ADMIN)
    public HotelResponse findById(@PathVariable Long id) {
        return hotelService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Authorities.ADMIN)
    public HotelResponse update(@PathVariable Long id, @Valid @RequestBody HotelRequest request) {
        return hotelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Authorities.ADMIN)
    public void delete(@PathVariable Long id) {
        hotelService.delete(id);
    }
}
