package com.hotelbooking.hotel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.hotel.dto.RoomCreateRequest;
import com.hotelbooking.hotel.dto.RoomUpdateRequest;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class RoomControllerTest extends IntegrationTest {

    @Test
    void createRoom_returns201WithDefaultAvailability_whenAdmin() throws Exception {
        Hotel hotel = hotel("Grand Plaza", "Moscow");
        RoomCreateRequest request = new RoomCreateRequest(hotel.getId(), "101", null);

        mockMvc.perform(post("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hotelId").value(hotel.getId()))
                .andExpect(jsonPath("$.number").value("101"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.timesBooked").value(0));
    }

    @Test
    void createRoom_returns404_whenHotelAbsent() throws Exception {
        RoomCreateRequest request = new RoomCreateRequest(999_999L, "101", true);

        mockMvc.perform(post("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRoom_returns409_whenNumberDuplicatedInHotel() throws Exception {
        Hotel hotel = hotel("Grand Plaza", "Moscow");
        room(hotel, "101", true, 0);
        RoomCreateRequest request = new RoomCreateRequest(hotel.getId(), "101", true);

        mockMvc.perform(post("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void createRoom_returns400_whenRequiredFieldsMissing() throws Exception {
        RoomCreateRequest request = new RoomCreateRequest(null, "", true);

        mockMvc.perform(post("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hotelId").exists())
                .andExpect(jsonPath("$.fieldErrors.number").exists());
    }

    @Test
    void createRoom_returns403_whenUser() throws Exception {
        Hotel hotel = hotel("Grand Plaza", "Moscow");
        RoomCreateRequest request = new RoomCreateRequest(hotel.getId(), "101", true);

        mockMvc.perform(post("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, tokens.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRoom_returnsRoom_whenUser() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 3);

        mockMvc.perform(get("/api/rooms/{id}", room.getId()).header(HttpHeaders.AUTHORIZATION, tokens.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("101"))
                .andExpect(jsonPath("$.timesBooked").value(3));
    }

    @Test
    void getRoom_returns404_whenAbsent() throws Exception {
        long missingId = 999_999L;

        mockMvc.perform(get("/api/rooms/{id}", missingId).header(HttpHeaders.AUTHORIZATION, tokens.user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRoom_changesNumberAndAvailability_whenAdmin() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        RoomUpdateRequest request = new RoomUpdateRequest("111", false);

        mockMvc.perform(put("/api/rooms/{id}", room.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("111"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void updateRoom_returns409_whenNumberTakenByAnotherRoom() throws Exception {
        Hotel hotel = hotel("Grand Plaza", "Moscow");
        room(hotel, "101", true, 0);
        Room room = room(hotel, "102", true, 0);
        RoomUpdateRequest request = new RoomUpdateRequest("101", true);

        mockMvc.perform(put("/api/rooms/{id}", room.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateRoom_returns400_whenAvailabilityMissing() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        RoomUpdateRequest request = new RoomUpdateRequest("101", null);

        mockMvc.perform(put("/api/rooms/{id}", room.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.available").exists());
    }

    @Test
    void deleteRoom_returns204_whenNoHeldReservations() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);

        mockMvc.perform(delete("/api/rooms/{id}", room.getId()).header(HttpHeaders.AUTHORIZATION, tokens.admin()))
                .andExpect(status().isNoContent());

        assertThat(roomRepository.existsById(room.getId())).isFalse();
    }

    @Test
    void deleteRoom_returns409_whenHeldReservationExists() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 1);
        heldReservation(room, 1L, BASE_DATE, BASE_DATE.plusDays(1));

        mockMvc.perform(delete("/api/rooms/{id}", room.getId()).header(HttpHeaders.AUTHORIZATION, tokens.admin()))
                .andExpect(status().isConflict());

        assertThat(roomRepository.existsById(room.getId())).isTrue();
    }

    @Test
    void freeRooms_excludeUnavailableAndOverlappingRooms() throws Exception {
        Hotel hotel = hotel("Grand Plaza", "Moscow");
        Room free = room(hotel, "101", true, 0);
        Room adjacent = room(hotel, "102", true, 0);
        Room overlapping = room(hotel, "103", true, 0);
        room(hotel, "104", false, 0);
        heldReservation(adjacent, 1L, BASE_DATE.minusDays(2), BASE_DATE);
        heldReservation(overlapping, 2L, BASE_DATE.plusDays(2), BASE_DATE.plusDays(5));

        searchFree("/api/rooms", BASE_DATE, BASE_DATE.plusDays(3), tokens.user())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id")
                        .value(containsInAnyOrder(
                                free.getId().intValue(), adjacent.getId().intValue())));
    }

    @Test
    void freeRooms_ignoreReleasedReservations() throws Exception {
        Room room = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        releasedReservation(room, 1L, BASE_DATE, BASE_DATE.plusDays(3));

        searchFree("/api/rooms", BASE_DATE, BASE_DATE.plusDays(3), tokens.internal())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(room.getId().intValue())));
    }

    @Test
    void freeRooms_filterByHotel() throws Exception {
        Room plazaRoom = room(hotel("Grand Plaza", "Moscow"), "101", true, 0);
        room(hotel("Sea Breeze", "Sochi"), "201", true, 0);

        mockMvc.perform(get("/api/rooms")
                        .param("startDate", BASE_DATE.toString())
                        .param("endDate", BASE_DATE.plusDays(1).toString())
                        .param("hotelId", plazaRoom.getHotel().getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, tokens.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(plazaRoom.getId().intValue())));
    }

    @Test
    void recommend_sortsByTimesBookedThenId() throws Exception {
        Hotel hotel = hotel("Grand Plaza", "Moscow");
        Room popular = room(hotel, "101", true, 5);
        Room firstUnpopular = room(hotel, "102", true, 1);
        Room secondUnpopular = room(hotel, "103", true, 1);
        Room fresh = room(hotel, "104", true, 0);

        searchFree("/api/rooms/recommend", BASE_DATE, BASE_DATE.plusDays(2), tokens.user())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id")
                        .value(contains(
                                fresh.getId().intValue(),
                                firstUnpopular.getId().intValue(),
                                secondUnpopular.getId().intValue(),
                                popular.getId().intValue())));
    }

    @Test
    void freeRooms_returns400_whenEndDateNotAfterStartDate() throws Exception {
        room(hotel("Grand Plaza", "Moscow"), "101", true, 0);

        searchFree("/api/rooms", BASE_DATE, BASE_DATE, tokens.user())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists());
    }

    @Test
    void freeRooms_returns400_whenDatesMissing() throws Exception {
        String missingDatesPath = "/api/rooms/recommend";

        mockMvc.perform(get(missingDatesPath).header(HttpHeaders.AUTHORIZATION, tokens.user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startDate").exists())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists());
    }

    @Test
    void recommend_returns400WithFixedFieldMessage_whenHotelIdNotNumeric() throws Exception {
        String token = tokens.user();

        ResultActions result = mockMvc.perform(get("/api/rooms/recommend")
                .param("startDate", BASE_DATE.toString())
                .param("endDate", BASE_DATE.plusDays(1).toString())
                .param("hotelId", "abc")
                .header(HttpHeaders.AUTHORIZATION, token));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.hotelId").value("Invalid value"));
    }

    @Test
    void freeRooms_returns401_whenTokenMissing() throws Exception {
        String path = "/api/rooms";

        mockMvc.perform(get(path)
                        .param("startDate", BASE_DATE.toString())
                        .param("endDate", BASE_DATE.plusDays(1).toString()))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions searchFree(String path, LocalDate start, LocalDate end, String token) throws Exception {
        return mockMvc.perform(get(path)
                .param("startDate", start.toString())
                .param("endDate", end.toString())
                .header(HttpHeaders.AUTHORIZATION, token));
    }
}
