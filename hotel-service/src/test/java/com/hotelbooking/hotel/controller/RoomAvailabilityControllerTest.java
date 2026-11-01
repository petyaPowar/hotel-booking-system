package com.hotelbooking.hotel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.hotel.dto.ConfirmAvailabilityRequest;
import com.hotelbooking.hotel.dto.ReleaseRequest;
import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomReservation;
import com.hotelbooking.hotel.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class RoomAvailabilityControllerTest extends IntegrationTest {

    @Test
    void confirm_holdsRoomAndIncrementsTimesBooked() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 2);
        ConfirmAvailabilityRequest request = confirmRequest("booking-10:1", 10L, BASE_DATE, BASE_DATE.plusDays(3));

        confirm(room.getId(), request, tokens.internal())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").isNumber())
                .andExpect(jsonPath("$.requestId").value("booking-10:1"))
                .andExpect(jsonPath("$.bookingId").value(10))
                .andExpect(jsonPath("$.roomId").value(room.getId()))
                .andExpect(jsonPath("$.status").value("HELD"));

        assertThat(reload(room).getTimesBooked()).isEqualTo(3);
    }

    @Test
    void confirm_isIdempotent_whenSameRequestIdRepeated() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        ConfirmAvailabilityRequest request = confirmRequest("booking-11:1", 11L, BASE_DATE, BASE_DATE.plusDays(2));
        String firstReservationId = confirm(room.getId(), request, tokens.internal())
                .andReturn()
                .getResponse()
                .getContentAsString();

        confirm(room.getId(), request, tokens.internal())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId")
                        .value(objectMapper
                                .readTree(firstReservationId)
                                .get("reservationId")
                                .asLong()));

        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(reload(room).getTimesBooked()).isEqualTo(1);
    }

    @Test
    void confirm_returns409_whenRequestIdUsedForAnotherRoom() throws Exception {
        var hotel = hotel("Grand Plaza", "Moscow");
        Room first = room(hotel, "101", true, 0);
        Room second = room(hotel, "102", true, 0);
        ConfirmAvailabilityRequest request = confirmRequest("booking-12:1", 12L, BASE_DATE, BASE_DATE.plusDays(2));
        confirm(first.getId(), request, tokens.internal()).andExpect(status().isOk());

        confirm(second.getId(), request, tokens.internal()).andExpect(status().isConflict());

        assertThat(reload(second).getTimesBooked()).isZero();
    }

    @Test
    void confirm_returns409_whenRequestIdReusedByAnotherBooking() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        confirm(room.getId(), confirmRequest("30:1", 30L, BASE_DATE, BASE_DATE.plusDays(2)), tokens.internal())
                .andExpect(status().isOk());
        ConfirmAvailabilityRequest foreign = confirmRequest("30:1", 31L, BASE_DATE, BASE_DATE.plusDays(2));

        ResultActions result = confirm(room.getId(), foreign, tokens.internal());

        result.andExpect(status().isConflict());
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(reload(room).getTimesBooked()).isEqualTo(1);
    }

    @Test
    void confirm_returns409_whenRequestIdReplayedAfterRelease() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        ConfirmAvailabilityRequest request = confirmRequest("32:1", 32L, BASE_DATE, BASE_DATE.plusDays(2));
        confirm(room.getId(), request, tokens.internal()).andExpect(status().isOk());
        release(room.getId(), new ReleaseRequest("32:1", 32L)).andExpect(status().isOk());

        ResultActions result = confirm(room.getId(), request, tokens.internal());

        result.andExpect(status().isConflict());
        assertThat(reservationRepository.findByRequestId("32:1").orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
        assertThat(reload(room).getTimesBooked()).isZero();
    }

    @Test
    void confirm_returns409_whenDatesOverlapHeldReservation() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 1);
        heldReservation(room, 1L, BASE_DATE, BASE_DATE.plusDays(3));
        ConfirmAvailabilityRequest request =
                confirmRequest("booking-13:1", 13L, BASE_DATE.plusDays(2), BASE_DATE.plusDays(4));

        confirm(room.getId(), request, tokens.internal())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(reload(room).getTimesBooked()).isEqualTo(1);
    }

    @Test
    void confirm_succeeds_whenNewStayStartsOnCheckOutDay() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 1);
        heldReservation(room, 1L, BASE_DATE, BASE_DATE.plusDays(3));
        ConfirmAvailabilityRequest request =
                confirmRequest("booking-14:1", 14L, BASE_DATE.plusDays(3), BASE_DATE.plusDays(5));

        confirm(room.getId(), request, tokens.internal()).andExpect(status().isOk());

        assertThat(reload(room).getTimesBooked()).isEqualTo(2);
    }

    @Test
    void confirm_succeeds_whenOverlappingReservationReleased() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        releasedReservation(room, 1L, BASE_DATE, BASE_DATE.plusDays(3));
        ConfirmAvailabilityRequest request = confirmRequest("booking-15:1", 15L, BASE_DATE, BASE_DATE.plusDays(3));

        confirm(room.getId(), request, tokens.internal()).andExpect(status().isOk());

        assertThat(reload(room).getTimesBooked()).isEqualTo(1);
    }

    @Test
    void confirm_returns409_whenRoomUnavailable() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", false, 0);
        ConfirmAvailabilityRequest request = confirmRequest("booking-16:1", 16L, BASE_DATE, BASE_DATE.plusDays(1));

        confirm(room.getId(), request, tokens.internal()).andExpect(status().isConflict());

        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void confirm_returns404_whenRoomAbsent() throws Exception {
        ConfirmAvailabilityRequest request = confirmRequest("booking-17:1", 17L, BASE_DATE, BASE_DATE.plusDays(1));

        confirm(999_999L, request, tokens.internal()).andExpect(status().isNotFound());
    }

    @Test
    void confirm_returns403_whenCallerIsNotInternal() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        ConfirmAvailabilityRequest request = confirmRequest("booking-18:1", 18L, BASE_DATE, BASE_DATE.plusDays(1));

        confirm(room.getId(), request, tokens.admin()).andExpect(status().isForbidden());

        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void confirm_returns401_whenTokenMissing() throws Exception {
        ConfirmAvailabilityRequest request = confirmRequest("booking-19:1", 19L, BASE_DATE, BASE_DATE.plusDays(1));

        mockMvc.perform(post("/api/rooms/{id}/confirm-availability", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirm_returns400_whenEndDateBeforeStartDate() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        ConfirmAvailabilityRequest request = confirmRequest("booking-20:1", 20L, BASE_DATE, BASE_DATE.minusDays(1));

        confirm(room.getId(), request, tokens.internal())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists());
    }

    @Test
    void confirm_returns400_whenRequiredFieldsMissing() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        ConfirmAvailabilityRequest request = confirmRequest(" ", null, null, BASE_DATE);

        confirm(room.getId(), request, tokens.internal())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.requestId").exists())
                .andExpect(jsonPath("$.fieldErrors.bookingId").exists())
                .andExpect(jsonPath("$.fieldErrors.startDate").exists());
    }

    @Test
    void release_releasesHeldReservationsAndDecrementsTimesBooked() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 4);
        RoomReservation reservation = heldReservation(room, 21L, BASE_DATE, BASE_DATE.plusDays(2));

        release(room.getId(), new ReleaseRequest("booking-21:1", 21L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(21))
                .andExpect(jsonPath("$.roomId").value(room.getId()))
                .andExpect(jsonPath("$.releasedCount").value(1));

        RoomReservation released =
                reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(released.getReleasedAt()).isNotNull();
        assertThat(reload(room).getTimesBooked()).isEqualTo(3);
    }

    @Test
    void release_isIdempotent_whenRepeated() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 1);
        heldReservation(room, 22L, BASE_DATE, BASE_DATE.plusDays(2));
        ReleaseRequest request = new ReleaseRequest("booking-22:1", 22L);
        release(room.getId(), request).andExpect(status().isOk());

        release(room.getId(), request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedCount").value(0));

        assertThat(reload(room).getTimesBooked()).isZero();
    }

    @Test
    void release_leavesOtherBookingsHeld() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 1);
        RoomReservation other = heldReservation(room, 23L, BASE_DATE, BASE_DATE.plusDays(2));

        release(room.getId(), new ReleaseRequest("booking-24:1", 24L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedCount").value(0));

        assertThat(reservationRepository.findById(other.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.HELD);
        assertThat(reload(room).getTimesBooked()).isEqualTo(1);
    }

    @Test
    void confirm_returns409_whenSameRequestIdArrivesAfterReleaseWithoutHold() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        release(room.getId(), new ReleaseRequest("33:1", 33L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedCount").value(0));
        ConfirmAvailabilityRequest late = confirmRequest("33:1", 33L, BASE_DATE, BASE_DATE.plusDays(2));

        ResultActions result = confirm(room.getId(), late, tokens.internal());

        result.andExpect(status().isConflict());
        assertThat(reservationRepository.findByRequestId("33:1").orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
        assertThat(reload(room).getTimesBooked()).isZero();
    }

    @Test
    void release_recordsSingleTombstone_whenRepeatedWithoutHold() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        ReleaseRequest request = new ReleaseRequest("34:1", 34L);
        release(room.getId(), request).andExpect(status().isOk());

        release(room.getId(), request).andExpect(status().isOk());

        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void release_returns403_whenUser() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 1);

        mockMvc.perform(post("/api/rooms/{id}/release", room.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokens.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReleaseRequest("booking-25:1", 25L))))
                .andExpect(status().isForbidden());
    }

    private ConfirmAvailabilityRequest confirmRequest(
            String requestId, Long bookingId, LocalDate startDate, LocalDate endDate) {
        return new ConfirmAvailabilityRequest(requestId, bookingId, startDate, endDate);
    }

    private ResultActions confirm(Long roomId, ConfirmAvailabilityRequest request, String token) throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/confirm-availability", roomId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }

    private ResultActions release(Long roomId, ReleaseRequest request) throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/release", roomId)
                .header(HttpHeaders.AUTHORIZATION, tokens.internal())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }
}
