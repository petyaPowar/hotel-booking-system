package com.hotelbooking.booking.saga;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.hotelbooking.booking.HotelServiceStubs.releasePath;
import static com.hotelbooking.booking.HotelServiceStubs.released;
import static com.hotelbooking.booking.HotelServiceStubs.reservation;
import static com.hotelbooking.booking.HotelServiceStubs.stubConfirm;
import static com.hotelbooking.booking.HotelServiceStubs.stubRelease;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.booking.IntegrationTestSupport;
import com.hotelbooking.booking.dto.CreateBookingRequest;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.entity.UserRole;
import com.hotelbooking.booking.service.BookingStateService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

class BookingSagaCompensationTest extends IntegrationTestSupport {

    private static final long ROOM_ID = 3L;

    @MockitoSpyBean
    private BookingStateService stateService;

    @Test
    void createBooking_releasesRoomAndCancels_whenLocalConfirmationFails() throws Exception {
        User user = saveUser("guest", UserRole.USER);
        stubConfirm(HOTEL_SERVICE, ROOM_ID, reservation(ROOM_ID));
        stubRelease(HOTEL_SERVICE, ROOM_ID, released(ROOM_ID));
        doThrow(new IllegalStateException("Database unavailable"))
                .when(stateService)
                .markConfirmed(anyLong(), anyLong());
        CreateBookingRequest request = new CreateBookingRequest("req-db-down", ROOM_ID, false, START_DATE, END_DATE);

        ResultActions result = mockMvc.perform(post("/booking")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));

        result.andExpect(status().isInternalServerError());
        List<Booking> bookings = bookingRepository.findAll();
        assertThat(bookings).hasSize(1);
        Booking booking = bookings.get(0);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        HOTEL_SERVICE.verify(
                1,
                postRequestedFor(urlEqualTo(releasePath(ROOM_ID)))
                        .withRequestBody(matchingJsonPath("$.requestId", equalTo(booking.getId() + ":" + ROOM_ID))));
    }
}
