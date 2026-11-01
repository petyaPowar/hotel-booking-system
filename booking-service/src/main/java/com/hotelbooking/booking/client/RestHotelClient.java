package com.hotelbooking.booking.client;

import com.hotelbooking.booking.client.dto.ConfirmAvailabilityRequest;
import com.hotelbooking.booking.client.dto.ReleaseRequest;
import com.hotelbooking.booking.client.dto.ReleaseResponse;
import com.hotelbooking.booking.client.dto.ReservationResponse;
import com.hotelbooking.booking.client.dto.RoomResponse;
import com.hotelbooking.booking.exception.HotelServiceUnavailableException;
import com.hotelbooking.booking.exception.RoomUnavailableException;
import com.hotelbooking.common.error.ApiException;
import com.hotelbooking.common.error.NotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestHotelClient implements HotelClient {

    private static final String HOTEL_SERVICE = "hotelService";
    private static final String HOTEL_RELEASE = "hotelRelease";
    private static final String CONFIRM_PATH = "/api/rooms/{id}/confirm-availability";
    private static final String RELEASE_PATH = "/api/rooms/{id}/release";
    private static final String RECOMMEND_PATH = "/api/rooms/recommend";
    private static final ParameterizedTypeReference<List<RoomResponse>> ROOM_LIST =
            new ParameterizedTypeReference<>() {};

    private final RestClient hotelRestClient;

    @Override
    @Retry(name = HOTEL_SERVICE, fallbackMethod = "confirmFallback")
    @CircuitBreaker(name = HOTEL_SERVICE)
    public ReservationResponse confirmAvailability(Long roomId, ConfirmAvailabilityRequest request) {
        return withErrorHandling(hotelRestClient
                        .post()
                        .uri(CONFIRM_PATH, roomId)
                        .body(request)
                        .retrieve())
                .body(ReservationResponse.class);
    }

    @Override
    @Retry(name = HOTEL_RELEASE, fallbackMethod = "releaseFallback")
    public ReleaseResponse release(Long roomId, ReleaseRequest request) {
        return withErrorHandling(hotelRestClient
                        .post()
                        .uri(RELEASE_PATH, roomId)
                        .body(request)
                        .retrieve())
                .body(ReleaseResponse.class);
    }

    @Override
    @Retry(name = HOTEL_SERVICE, fallbackMethod = "recommendFallback")
    @CircuitBreaker(name = HOTEL_SERVICE)
    public List<RoomResponse> recommend(LocalDate startDate, LocalDate endDate, Long hotelId) {
        RestClient.ResponseSpec response = hotelRestClient
                .get()
                .uri(builder -> builder.path(RECOMMEND_PATH)
                        .queryParam("startDate", startDate)
                        .queryParam("endDate", endDate)
                        .queryParamIfPresent("hotelId", Optional.ofNullable(hotelId))
                        .build())
                .retrieve();
        return withErrorHandling(response).body(ROOM_LIST);
    }

    private RestClient.ResponseSpec withErrorHandling(RestClient.ResponseSpec response) {
        return response.onStatus(status -> status.isSameCodeAs(HttpStatus.CONFLICT), (request, reply) -> {
                    throw new RoomUnavailableException("Room is not available for the requested dates");
                })
                .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, reply) -> {
                    throw new NotFoundException("Room not found");
                });
    }

    private ReservationResponse confirmFallback(Long roomId, ConfirmAvailabilityRequest request, Throwable ex) {
        throw translate(ex);
    }

    private ReleaseResponse releaseFallback(Long roomId, ReleaseRequest request, Throwable ex) {
        throw translate(ex);
    }

    private List<RoomResponse> recommendFallback(LocalDate startDate, LocalDate endDate, Long hotelId, Throwable ex) {
        throw translate(ex);
    }

    private ApiException translate(Throwable ex) {
        if (ex instanceof ApiException apiException) {
            return apiException;
        }
        log.warn("Hotel service call failed: {}", ex.toString());
        return new HotelServiceUnavailableException();
    }
}
