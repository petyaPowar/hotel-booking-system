package com.hotelbooking.hotel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.hotel.dto.HotelRequest;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class HotelControllerTest extends IntegrationTest {

    @Test
    void createHotel_returns201_whenAdmin() throws Exception {
        HotelRequest request = new HotelRequest("Grand Plaza", "Moscow");

        mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Grand Plaza"))
                .andExpect(jsonPath("$.address").value("Moscow"));

        assertThat(hotelRepository.count()).isEqualTo(1);
    }

    @Test
    void createHotel_returns401_whenTokenMissing() throws Exception {
        HotelRequest request = new HotelRequest("Grand Plaza", "Moscow");

        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void createHotel_returns401_whenTokenInvalid() throws Exception {
        HotelRequest request = new HotelRequest("Grand Plaza", "Moscow");

        mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getHotel_returns401_whenTokenHasNoExpiry() throws Exception {
        Hotel hotel = hotel("Grand Plaza", "Moscow");
        String token = tokens.userWithoutExpiry();

        ResultActions result =
                mockMvc.perform(get("/api/hotels/" + hotel.getId()).header(HttpHeaders.AUTHORIZATION, token));

        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void createHotel_returns403_whenUser() throws Exception {
        HotelRequest request = new HotelRequest("Grand Plaza", "Moscow");

        mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, tokens.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void createHotel_returns400_whenFieldsBlank() throws Exception {
        HotelRequest request = new HotelRequest(" ", "");

        mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.address").exists());
    }

    @Test
    void getHotel_returnsHotel_whenUser() throws Exception {
        Hotel hotel = hotel("Sea Breeze", "Sochi");

        mockMvc.perform(get("/api/hotels/{id}", hotel.getId()).header(HttpHeaders.AUTHORIZATION, tokens.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sea Breeze"));
    }

    @Test
    void getHotel_returns404_whenAbsent() throws Exception {
        long missingId = 999_999L;

        mockMvc.perform(get("/api/hotels/{id}", missingId).header(HttpHeaders.AUTHORIZATION, tokens.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getHotel_returns403_whenInternal() throws Exception {
        Hotel hotel = hotel("Sea Breeze", "Sochi");

        mockMvc.perform(get("/api/hotels/{id}", hotel.getId()).header(HttpHeaders.AUTHORIZATION, tokens.internal()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listHotels_filtersByNameIgnoringCaseAndPaginates() throws Exception {
        hotel("Grand Plaza", "Moscow");
        hotel("Grand Hotel", "Kazan");
        hotel("Sea Breeze", "Sochi");

        mockMvc.perform(get("/api/hotels")
                        .param("name", "GRAND")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "name,asc")
                        .header(HttpHeaders.AUTHORIZATION, tokens.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Grand Hotel"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void listHotels_filtersByAddress() throws Exception {
        hotel("Grand Plaza", "Moscow, Tverskaya");
        hotel("Sea Breeze", "Sochi");

        mockMvc.perform(get("/api/hotels")
                        .param("address", "tverskaya")
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Grand Plaza"));
    }

    @Test
    void listHotels_returns400_whenSortPropertyUnknown() throws Exception {
        hotel("Grand Plaza", "Moscow");

        mockMvc.perform(get("/api/hotels").param("sort", "unknown").header(HttpHeaders.AUTHORIZATION, tokens.user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid sort property 'unknown'"))
                .andExpect(jsonPath("$.path").value("/api/hotels"));
    }

    @Test
    void createHotel_returns415_whenBodyIsPlainText() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Grand Plaza"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void createHotel_returns400WithFixedMessage_whenJsonMalformed() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void updateHotel_returnsUpdatedHotel_whenAdmin() throws Exception {
        Hotel hotel = hotel("Old", "Old address");
        HotelRequest request = new HotelRequest("New", "New address");

        mockMvc.perform(put("/api/hotels/{id}", hotel.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"));

        assertThat(hotelRepository.findById(hotel.getId()).orElseThrow().getAddress())
                .isEqualTo("New address");
    }

    @Test
    void updateHotel_returns404_whenAbsent() throws Exception {
        HotelRequest request = new HotelRequest("New", "New address");

        mockMvc.perform(put("/api/hotels/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, tokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteHotel_returns204AndRemovesRoomsAndReleasedReservations() throws Exception {
        Hotel hotel = hotel("Grand Plaza", "Moscow");
        Room room = room(hotel, "101", true, 1);
        releasedReservation(room, 1L, BASE_DATE, BASE_DATE.plusDays(2));

        mockMvc.perform(delete("/api/hotels/{id}", hotel.getId()).header(HttpHeaders.AUTHORIZATION, tokens.admin()))
                .andExpect(status().isNoContent());

        assertThat(hotelRepository.count()).isZero();
        assertThat(roomRepository.count()).isZero();
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void deleteHotel_returns409_whenRoomHasHeldReservation() throws Exception {
        Hotel hotel = hotel("Grand Plaza", "Moscow");
        Room room = room(hotel, "101", true, 1);
        heldReservation(room, 1L, BASE_DATE, BASE_DATE.plusDays(2));

        mockMvc.perform(delete("/api/hotels/{id}", hotel.getId()).header(HttpHeaders.AUTHORIZATION, tokens.admin()))
                .andExpect(status().isConflict());

        assertThat(hotelRepository.count()).isEqualTo(1);
    }

    @Test
    void deleteHotel_returns403_whenUser() throws Exception {
        Hotel hotel = hotel("Grand Plaza", "Moscow");

        mockMvc.perform(delete("/api/hotels/{id}", hotel.getId()).header(HttpHeaders.AUTHORIZATION, tokens.user()))
                .andExpect(status().isForbidden());
    }
}
