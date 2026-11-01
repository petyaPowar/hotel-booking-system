package com.hotelbooking.booking.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.booking.IntegrationTestSupport;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.entity.UserRole;
import com.hotelbooking.common.web.RequestPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ErrorResponseTest extends IntegrationTestSupport {

    private User user;

    @BeforeEach
    void createUser() {
        user = saveUser("guest", UserRole.USER);
    }

    @Test
    void getBookings_returns400_whenSortPropertyUnknown() throws Exception {
        ResultActions result = mockMvc.perform(
                get("/bookings").param("sort", "unknown").header(HttpHeaders.AUTHORIZATION, bearer(user)));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid sort property 'unknown'"))
                .andExpect(jsonPath("$.path").value("/bookings"));
    }

    @Test
    void createBooking_returns415_whenBodyIsPlainText() throws Exception {
        ResultActions result = mockMvc.perform(post("/booking")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.TEXT_PLAIN)
                .content("book me a room"));

        result.andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.path").value("/booking"));
    }

    @Test
    void createBooking_returns400WithFixedMessage_whenJsonMalformed() throws Exception {
        ResultActions result = mockMvc.perform(post("/booking")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\": "));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void getBooking_returns400NamingParameter_whenIdNotNumeric() throws Exception {
        ResultActions result = mockMvc.perform(get("/booking/abc").header(HttpHeaders.AUTHORIZATION, bearer(user)));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
    }

    @Test
    void recommendations_return400WithFixedFieldMessage_whenHotelIdNotNumeric() throws Exception {
        ResultActions result = mockMvc.perform(get("/booking/recommendations")
                .param("startDate", START_DATE.toString())
                .param("endDate", END_DATE.toString())
                .param("hotelId", "abc")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.hotelId").value("Invalid value"));
    }

    @Test
    void unknownPath_returns404WithFixedMessage() throws Exception {
        ResultActions result = mockMvc.perform(get("/no-such-path").header(HttpHeaders.AUTHORIZATION, bearer(user)));

        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.path").value("/no-such-path"));
    }

    @Test
    void errorPath_includesForwardedPrefix_whenGatewayStrippedIt() throws Exception {
        ResultActions result = mockMvc.perform(post("/booking")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header(RequestPaths.FORWARDED_PREFIX_HEADER, "/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\": "));

        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.path").value("/api/bookings/booking"));
    }

    @Test
    void unauthorizedPath_includesForwardedPrefix_whenGatewayStrippedIt() throws Exception {
        ResultActions result =
                mockMvc.perform(get("/bookings").header(RequestPaths.FORWARDED_PREFIX_HEADER, "/api/bookings"));

        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.path").value("/api/bookings/bookings"));
    }

    @Test
    void createBooking_returns405_whenMethodNotSupported() throws Exception {
        ResultActions result = mockMvc.perform(put("/booking").header(HttpHeaders.AUTHORIZATION, bearer(user)));

        result.andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }
}
