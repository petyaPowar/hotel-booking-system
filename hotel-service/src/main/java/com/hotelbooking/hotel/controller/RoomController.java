package com.hotelbooking.hotel.controller;

import com.hotelbooking.common.security.Authorities;
import com.hotelbooking.hotel.dto.FreeRoomQuery;
import com.hotelbooking.hotel.dto.RoomCreateRequest;
import com.hotelbooking.hotel.dto.RoomOccupancyResponse;
import com.hotelbooking.hotel.dto.RoomResponse;
import com.hotelbooking.hotel.dto.RoomStatsQuery;
import com.hotelbooking.hotel.dto.RoomUpdateRequest;
import com.hotelbooking.hotel.dto.StatsPeriod;
import com.hotelbooking.hotel.service.OccupancyStatsService;
import com.hotelbooking.hotel.service.RoomService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final OccupancyStatsService statsService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Authorities.ADMIN)
    public RoomResponse create(@Valid @RequestBody RoomCreateRequest request) {
        return roomService.create(request);
    }

    @GetMapping
    @PreAuthorize(Authorities.USER_ADMIN_OR_INTERNAL)
    public List<RoomResponse> findFree(@Valid FreeRoomQuery query) {
        return roomService.findFree(query);
    }

    @GetMapping("/recommend")
    @PreAuthorize(Authorities.USER_ADMIN_OR_INTERNAL)
    public List<RoomResponse> recommend(@Valid FreeRoomQuery query) {
        return roomService.recommend(query);
    }

    @GetMapping("/stats")
    @PreAuthorize(Authorities.ADMIN)
    public List<RoomOccupancyResponse> stats(@Valid StatsPeriod period, @Valid RoomStatsQuery query) {
        return statsService.roomStats(period, query);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Authorities.USER_OR_ADMIN)
    public RoomResponse findById(@PathVariable Long id) {
        return roomService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Authorities.ADMIN)
    public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomUpdateRequest request) {
        return roomService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Authorities.ADMIN)
    public void delete(@PathVariable Long id) {
        roomService.delete(id);
    }
}
