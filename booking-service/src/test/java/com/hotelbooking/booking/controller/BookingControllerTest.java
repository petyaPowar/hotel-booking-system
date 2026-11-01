package com.hotelbooking.booking.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.hotelbooking.booking.HotelServiceStubs.releasePath;
import static com.hotelbooking.booking.HotelServiceStubs.released;
import static com.hotelbooking.booking.HotelServiceStubs.room;
import static com.hotelbooking.booking.HotelServiceStubs.rooms;
import static com.hotelbooking.booking.HotelServiceStubs.stubRecommend;
import static com.hotelbooking.booking.HotelServiceStubs.stubRelease;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelbooking.booking.IntegrationTestSupport;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.entity.UserRole;
import com.hotelbooking.common.security.JwtProperties;
import com.hotelbooking.common.security.Roles;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.ResultActions;

class BookingControllerTest extends IntegrationTestSupport {

    private static final long ROOM_ID = 1L;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    private User owner;
    private User stranger;
    private User admin;

    @BeforeEach
    void createUsers() {
        owner = saveUser("owner", UserRole.USER);
        stranger = saveUser("stranger", UserRole.USER);
        admin = saveUser("admin", UserRole.ADMIN);
    }

    @Test
    void getBooking_returnsBooking_whenCalledByOwner() throws Exception {
        Booking booking = saveBooking(owner, ROOM_ID, BookingStatus.CONFIRMED, "own");

        ResultActions result =
                mockMvc.perform(get("/booking/" + booking.getId()).header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.userId").value(owner.getId()));
    }

    @Test
    void getBooking_returns403_whenBookingBelongsToAnotherUser() throws Exception {
        Booking booking = saveBooking(owner, ROOM_ID, BookingStatus.CONFIRMED, "foreign");

        ResultActions result =
                mockMvc.perform(get("/booking/" + booking.getId()).header(HttpHeaders.AUTHORIZATION, bearer(stranger)));

        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getBooking_returnsBooking_whenCalledByAdmin() throws Exception {
        Booking booking = saveBooking(owner, ROOM_ID, BookingStatus.CONFIRMED, "admin-view");

        ResultActions result =
                mockMvc.perform(get("/booking/" + booking.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)));

        result.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(booking.getId()));
    }

    @Test
    void getBooking_returns404_whenAbsent() throws Exception {
        String path = "/booking/999999";

        ResultActions result = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        result.andExpect(status().isNotFound());
    }

    @Test
    void getBookings_returnsOnlyOwnBookingsPaged() throws Exception {
        saveBooking(owner, 1L, BookingStatus.CONFIRMED, "page-1");
        saveBooking(owner, 2L, BookingStatus.CANCELLED, "page-2");
        saveBooking(owner, 3L, BookingStatus.CONFIRMED, "page-3");
        saveBooking(stranger, 4L, BookingStatus.CONFIRMED, "foreign");

        ResultActions result = mockMvc.perform(get("/bookings")
                .param("page", "0")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[*].userId")
                        .value(everyItem(is(owner.getId().intValue()))))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void getBookings_returns401_whenTokenHasNoUserId() throws Exception {
        String token = "Bearer " + tokenWithoutUserId();

        ResultActions result = mockMvc.perform(get("/bookings").header(HttpHeaders.AUTHORIZATION, token));

        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/bookings"));
    }

    @Test
    void getBookings_returns401_withoutToken() throws Exception {
        String path = "/bookings";

        ResultActions result = mockMvc.perform(get(path));

        result.andExpect(status().isUnauthorized());
    }

    @Test
    void cancelBooking_releasesRoomAndCancels_whenConfirmed() throws Exception {
        Booking booking = saveBooking(owner, ROOM_ID, BookingStatus.CONFIRMED, "to-cancel");
        stubRelease(HOTEL_SERVICE, ROOM_ID, released(ROOM_ID));

        ResultActions result =
                mockMvc.perform(delete("/booking/" + booking.getId()).header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        result.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        HOTEL_SERVICE.verify(
                1,
                postRequestedFor(urlEqualTo(releasePath(ROOM_ID)))
                        .withRequestBody(matchingJsonPath(
                                "$.bookingId", equalTo(booking.getId().toString())))
                        .withRequestBody(matchingJsonPath("$.requestId", equalTo(booking.getId() + ":" + ROOM_ID))));
        assertThat(statusOf(booking)).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBooking_returns409AndKeepsPending_whenBookingPending() throws Exception {
        Booking booking = saveBooking(owner, ROOM_ID, BookingStatus.PENDING, "in-progress");

        ResultActions result =
                mockMvc.perform(delete("/booking/" + booking.getId()).header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        result.andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertThat(statusOf(booking)).isEqualTo(BookingStatus.PENDING);
        assertThat(HOTEL_SERVICE.getAllServeEvents()).isEmpty();
    }

    @Test
    void cancelBooking_releasesRoomAndCancelsPending_whenCalledByAdmin() throws Exception {
        Booking booking = saveBooking(owner, ROOM_ID, BookingStatus.PENDING, "stuck");
        stubRelease(HOTEL_SERVICE, ROOM_ID, released(ROOM_ID));

        ResultActions result =
                mockMvc.perform(delete("/booking/" + booking.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)));

        result.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        HOTEL_SERVICE.verify(
                1,
                postRequestedFor(urlEqualTo(releasePath(ROOM_ID)))
                        .withRequestBody(matchingJsonPath("$.requestId", equalTo(booking.getId() + ":" + ROOM_ID))));
        assertThat(statusOf(booking)).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBooking_returnsUnchanged_whenAlreadyCancelled() throws Exception {
        Booking booking = saveBooking(owner, ROOM_ID, BookingStatus.CANCELLED, "cancelled");

        ResultActions result =
                mockMvc.perform(delete("/booking/" + booking.getId()).header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        result.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(HOTEL_SERVICE.getAllServeEvents()).isEmpty();
    }

    @Test
    void cancelBooking_returns403_whenBookingBelongsToAnotherUser() throws Exception {
        Booking booking = saveBooking(owner, ROOM_ID, BookingStatus.CONFIRMED, "protected");

        ResultActions result = mockMvc.perform(
                delete("/booking/" + booking.getId()).header(HttpHeaders.AUTHORIZATION, bearer(stranger)));

        result.andExpect(status().isForbidden());
        assertThat(statusOf(booking)).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void cancelBooking_returns503AndKeepsConfirmed_whenHotelUnavailable() throws Exception {
        Booking booking = saveBooking(owner, ROOM_ID, BookingStatus.CONFIRMED, "hotel-down");
        stubRelease(HOTEL_SERVICE, ROOM_ID, serverError());

        ResultActions result =
                mockMvc.perform(delete("/booking/" + booking.getId()).header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        result.andExpect(status().isServiceUnavailable());
        assertThat(statusOf(booking)).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void recommendations_returnRoomsFromHotelService() throws Exception {
        stubRecommend(HOTEL_SERVICE, rooms(room(3, 0), room(1, 5)));

        ResultActions result = mockMvc.perform(get("/booking/recommendations")
                .param("startDate", START_DATE.toString())
                .param("endDate", END_DATE.toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[1].timesBooked").value(5));
    }

    @Test
    void recommendations_sortByTimesBookedThenId_whenHotelReturnsRoomsOutOfOrder() throws Exception {
        stubRecommend(HOTEL_SERVICE, rooms(room(5, 3), room(6, 0), room(3, 0), room(1, 1)));

        ResultActions result = mockMvc.perform(get("/booking/recommendations")
                .param("startDate", START_DATE.toString())
                .param("endDate", END_DATE.toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        result.andExpect(status().isOk()).andExpect(jsonPath("$[*].id").value(contains(3, 6, 1, 5)));
    }

    @Test
    void recommendations_return400_whenEndDateNotAfterStartDate() throws Exception {
        String sameDay = START_DATE.toString();

        ResultActions result = mockMvc.perform(get("/booking/recommendations")
                .param("startDate", sameDay)
                .param("endDate", sameDay)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists());
        assertThat(HOTEL_SERVICE.getAllServeEvents()).isEmpty();
    }

    private String tokenWithoutUserId() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(owner.getUsername())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(jwtProperties.ttl()))
                .claim(Roles.ROLES_CLAIM, List.of(Roles.USER))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private BookingStatus statusOf(Booking booking) {
        return bookingRepository.findById(booking.getId()).orElseThrow().getStatus();
    }
}
