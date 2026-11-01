package com.hotelbooking.booking.controller;

import static com.hotelbooking.booking.HotelServiceStubs.released;
import static com.hotelbooking.booking.HotelServiceStubs.stubRelease;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.booking.IntegrationTestSupport;
import com.hotelbooking.booking.dto.BookingResponse;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.entity.UserRole;
import com.hotelbooking.booking.service.BookingStateService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

class BookingCancelRaceTest extends IntegrationTestSupport {

    private static final long ROOM_ID = 1L;

    @MockitoSpyBean
    private BookingStateService stateService;

    @Test
    void cancelBooking_returns200Unchanged_whenConcurrentCancelFinishedFirst() throws Exception {
        User owner = saveUser("owner", UserRole.USER);
        Booking booking = saveBooking(owner, ROOM_ID, BookingStatus.CONFIRMED, "double-cancel");
        BookingResponse staleSnapshot = stateService.getById(booking.getId());
        stateService.markCancelled(booking.getId());
        doReturn(staleSnapshot).when(stateService).getById(booking.getId());
        stubRelease(HOTEL_SERVICE, ROOM_ID, released(ROOM_ID));

        ResultActions result =
                mockMvc.perform(delete("/booking/" + booking.getId()).header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }
}
