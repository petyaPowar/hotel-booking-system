package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.client.dto.RoomResponse;
import com.hotelbooking.booking.dto.BookingCreationResult;
import com.hotelbooking.booking.dto.BookingResponse;
import com.hotelbooking.booking.dto.CreateBookingRequest;
import com.hotelbooking.booking.dto.RecommendationRequest;
import com.hotelbooking.booking.security.CurrentUser;
import com.hotelbooking.booking.service.BookingCreationService;
import com.hotelbooking.booking.service.BookingService;
import com.hotelbooking.common.security.Authorities;
import com.hotelbooking.common.web.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingCreationService bookingCreationService;
    private final BookingService bookingService;

    @PostMapping("/booking")
    @PreAuthorize(Authorities.USER_OR_ADMIN)
    public ResponseEntity<BookingResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBookingRequest request) {
        BookingCreationResult result =
                bookingCreationService.create(CurrentUser.from(jwt).id(), request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.booking());
    }

    @GetMapping("/bookings")
    @PreAuthorize(Authorities.USER_OR_ADMIN)
    public PageResponse<BookingResponse> findOwn(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return bookingService.findOwn(CurrentUser.from(jwt).id(), pageable);
    }

    @GetMapping("/booking/recommendations")
    @PreAuthorize(Authorities.USER_OR_ADMIN)
    public List<RoomResponse> recommend(@Valid @ModelAttribute RecommendationRequest request) {
        return bookingService.recommend(request);
    }

    @GetMapping("/booking/{id}")
    @PreAuthorize(Authorities.USER_OR_ADMIN)
    public BookingResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookingService.findById(id, CurrentUser.from(jwt));
    }

    @DeleteMapping("/booking/{id}")
    @PreAuthorize(Authorities.USER_OR_ADMIN)
    public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookingService.cancel(id, CurrentUser.from(jwt));
    }
}
