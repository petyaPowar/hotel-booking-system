package com.hotelbooking.booking.client;

import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.hotelbooking.booking.HotelServiceStubs.RECOMMEND_PATH;
import static com.hotelbooking.booking.HotelServiceStubs.confirmPath;
import static com.hotelbooking.booking.HotelServiceStubs.conflict;
import static com.hotelbooking.booking.HotelServiceStubs.stubConfirm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.booking.IntegrationTestSupport;
import com.hotelbooking.booking.dto.CreateBookingRequest;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.entity.UserRole;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class HotelCircuitBreakerTest extends IntegrationTestSupport {

    private static final String HOTEL_CIRCUIT_BREAKER = "hotelService";
    private static final int FORBIDDEN_CALLS = 6;
    private static final int FAILING_CALLS = 2;
    private static final int HALF_OPEN_CALLS = 5;
    private static final long ROOM_ID = 3L;

    @Test
    void circuitBreaker_staysClosed_whenHotelRepeatedlyReturns403() throws Exception {
        User user = saveUser("guest", UserRole.USER);
        HOTEL_SERVICE.stubFor(get(urlPathEqualTo(RECOMMEND_PATH)).willReturn(forbidden()));
        for (int call = 0; call < FORBIDDEN_CALLS; call++) {
            recommend(user).andExpect(status().isServiceUnavailable());
        }

        ResultActions next = recommend(user);

        next.andExpect(status().isServiceUnavailable());
        HOTEL_SERVICE.verify(FORBIDDEN_CALLS + 1, getRequestedFor(urlPathEqualTo(RECOMMEND_PATH)));
        assertThat(circuitBreakerRegistry.circuitBreaker(HOTEL_CIRCUIT_BREAKER).getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void circuitBreaker_opensAndStopsCallingHotel_whenHotelRepeatedlyReturns5xx() throws Exception {
        User user = saveUser("guest", UserRole.USER);
        HOTEL_SERVICE.stubFor(get(urlPathEqualTo(RECOMMEND_PATH)).willReturn(serverError()));
        for (int call = 0; call < FAILING_CALLS; call++) {
            recommend(user).andExpect(status().isServiceUnavailable());
        }
        int callsBeforeOpen = HOTEL_SERVICE
                .countRequestsMatching(
                        getRequestedFor(urlPathEqualTo(RECOMMEND_PATH)).build())
                .getCount();

        ResultActions next = recommend(user);

        next.andExpect(status().isServiceUnavailable());
        assertThat(circuitBreakerRegistry.circuitBreaker(HOTEL_CIRCUIT_BREAKER).getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        HOTEL_SERVICE.verify(callsBeforeOpen, getRequestedFor(urlPathEqualTo(RECOMMEND_PATH)));
    }

    @Test
    void circuitBreaker_closesFromHalfOpen_whenHotelReturnsBusinessConflicts() throws Exception {
        User user = saveUser("guest", UserRole.USER);
        stubConfirm(HOTEL_SERVICE, ROOM_ID, conflict());
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(HOTEL_CIRCUIT_BREAKER);
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();

        for (int call = 0; call < HALF_OPEN_CALLS; call++) {
            book(user, "half-open-" + call).andExpect(status().isConflict());
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        HOTEL_SERVICE.verify(HALF_OPEN_CALLS, postRequestedFor(urlEqualTo(confirmPath(ROOM_ID))));
    }

    private ResultActions book(User user, String requestId) throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(requestId, ROOM_ID, false, START_DATE, END_DATE);
        return mockMvc.perform(MockMvcRequestBuilders.post("/booking")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }

    private ResultActions recommend(User user) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get("/booking/recommendations")
                .param("startDate", START_DATE.toString())
                .param("endDate", END_DATE.toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }
}
