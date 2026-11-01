package com.hotelbooking.hotel.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class OccupancyStatsControllerTest extends IntegrationTest {

    private Hotel plaza;
    private Hotel breeze;
    private Hotel neva;
    private Room plazaAvailable;
    private Room plazaClosed;
    private Room breezeRoom;

    @BeforeEach
    void seedOccupancy() {
        plaza = hotel("Grand Plaza", "Moscow");
        breeze = hotel("Sea Breeze", "Sochi");
        neva = hotel("Neva View", "Saint Petersburg");
        plazaAvailable = room(plaza, "101", true, 2);
        plazaClosed = room(plaza, "102", false, 1);
        breezeRoom = room(breeze, "201", true, 0);
        heldReservation(plazaAvailable, 1L, BASE_DATE.minusDays(2), BASE_DATE.plusDays(3));
        heldReservation(plazaAvailable, 2L, BASE_DATE.plusDays(8), BASE_DATE.plusDays(12));
        heldReservation(plazaAvailable, 3L, BASE_DATE.plusDays(20), BASE_DATE.plusDays(22));
        releasedReservation(plazaClosed, 4L, BASE_DATE, BASE_DATE.plusDays(5));
        heldReservation(breezeRoom, 5L, BASE_DATE, BASE_DATE.plusDays(7));
    }

    @Test
    void hotelStats_computesOccupancyAndSortsByRateDescending() throws Exception {
        MockHttpServletRequestBuilder request = statsRequest("/api/hotels/stats");

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].hotelId")
                        .value(contains(
                                breeze.getId().intValue(),
                                plaza.getId().intValue(),
                                neva.getId().intValue())))
                .andExpect(jsonPath("$[0].bookedNights").value(7))
                .andExpect(jsonPath("$[0].occupancyRate").value(0.7))
                .andExpect(jsonPath("$[1].name").value("Grand Plaza"))
                .andExpect(jsonPath("$[1].totalRooms").value(2))
                .andExpect(jsonPath("$[1].availableRooms").value(1))
                .andExpect(jsonPath("$[1].totalTimesBooked").value(3))
                .andExpect(jsonPath("$[1].bookedNights").value(5))
                .andExpect(jsonPath("$[1].occupancyRate").value(0.25))
                .andExpect(jsonPath("$[2].totalRooms").value(0))
                .andExpect(jsonPath("$[2].occupancyRate").value(0.0));
    }

    @Test
    void roomStats_sortsByTimesBookedAscendingByDefault() throws Exception {
        MockHttpServletRequestBuilder request = statsRequest("/api/rooms/stats");

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].roomId")
                        .value(contains(
                                breezeRoom.getId().intValue(),
                                plazaClosed.getId().intValue(),
                                plazaAvailable.getId().intValue())))
                .andExpect(jsonPath("$[2].activeReservations").value(2))
                .andExpect(jsonPath("$[2].bookedNights").value(5))
                .andExpect(jsonPath("$[2].occupancyRate").value(0.5))
                .andExpect(jsonPath("$[1].activeReservations").value(0))
                .andExpect(jsonPath("$[1].bookedNights").value(0));
    }

    @Test
    void roomStats_sortsByOccupancyRateDescending() throws Exception {
        MockHttpServletRequestBuilder request = statsRequest("/api/rooms/stats")
                .param("sortBy", "occupancyRate")
                .param("direction", "desc");

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].roomId")
                        .value(contains(
                                breezeRoom.getId().intValue(),
                                plazaAvailable.getId().intValue(),
                                plazaClosed.getId().intValue())));
    }

    @Test
    void roomStats_filtersByHotelAndSortsById() throws Exception {
        MockHttpServletRequestBuilder request = statsRequest("/api/rooms/stats")
                .param("hotelId", plaza.getId().toString())
                .param("sortBy", "id");

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].roomId")
                        .value(contains(
                                plazaAvailable.getId().intValue(),
                                plazaClosed.getId().intValue())));
    }

    @Test
    void roomStats_returns400_whenSortFieldUnknown() throws Exception {
        MockHttpServletRequestBuilder request = statsRequest("/api/rooms/stats").param("sortBy", "price");

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sortBy").exists());
    }

    @Test
    void hotelStats_returns400_whenPeriodInverted() throws Exception {
        MockHttpServletRequestBuilder request = get("/api/hotels/stats")
                .param("from", BASE_DATE.toString())
                .param("to", BASE_DATE.minusDays(1).toString())
                .header(HttpHeaders.AUTHORIZATION, tokens.admin());

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.to").exists());
    }

    @Test
    void hotelStats_returns403_whenUser() throws Exception {
        MockHttpServletRequestBuilder request = statsRequest("/api/hotels/stats", tokens.user());

        mockMvc.perform(request).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder statsRequest(String path) {
        return statsRequest(path, tokens.admin());
    }

    private MockHttpServletRequestBuilder statsRequest(String path, String token) {
        return get(path)
                .param("from", BASE_DATE.toString())
                .param("to", BASE_DATE.plusDays(10).toString())
                .header(HttpHeaders.AUTHORIZATION, token);
    }
}
