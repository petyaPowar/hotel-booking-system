package com.hotelbooking.booking.saga;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.hotelbooking.booking.HotelServiceStubs.confirmPath;
import static com.hotelbooking.booking.HotelServiceStubs.conflict;
import static com.hotelbooking.booking.HotelServiceStubs.releasePath;
import static com.hotelbooking.booking.HotelServiceStubs.released;
import static com.hotelbooking.booking.HotelServiceStubs.reservation;
import static com.hotelbooking.booking.HotelServiceStubs.room;
import static com.hotelbooking.booking.HotelServiceStubs.rooms;
import static com.hotelbooking.booking.HotelServiceStubs.stubConfirm;
import static com.hotelbooking.booking.HotelServiceStubs.stubRecommend;
import static com.hotelbooking.booking.HotelServiceStubs.stubRelease;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.hotelbooking.booking.HotelServiceStubs;
import com.hotelbooking.booking.IntegrationTestSupport;
import com.hotelbooking.booking.dto.CreateBookingRequest;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.entity.UserRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@ExtendWith(OutputCaptureExtension.class)
class BookingSagaTest extends IntegrationTestSupport {

    private static final long ROOM_ID = 3L;
    private static final long NEXT_ROOM_ID = 6L;
    private static final int TIMEOUT_DELAY_MS = 1500;
    private static final int MAX_ATTEMPTS = 3;
    private static final int CONCURRENT_REQUESTS = 5;
    private static final String HOTEL_CIRCUIT_BREAKER = "hotelService";

    private User user;

    @BeforeEach
    void createUser() {
        user = saveUser("guest", UserRole.USER);
    }

    @Test
    void createBooking_returns201AndConfirms_whenHotelConfirmsRoom() throws Exception {
        stubConfirm(HOTEL_SERVICE, ROOM_ID, reservation(ROOM_ID));

        ResultActions result = book(manualRequest("req-ok"));

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.requestId").value("req-ok"));
        Booking booking = singleBooking();
        HOTEL_SERVICE.verify(
                1,
                postRequestedFor(urlEqualTo(confirmPath(ROOM_ID)))
                        .withHeader(HttpHeaders.AUTHORIZATION, matching("Bearer .+"))
                        .withRequestBody(matchingJsonPath("$.requestId", equalTo(booking.getId() + ":" + ROOM_ID)))
                        .withRequestBody(matchingJsonPath("$.startDate", equalTo(START_DATE.toString()))));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void createBooking_returns409AndCancels_whenRoomAlreadyHeld() throws Exception {
        stubConfirm(HOTEL_SERVICE, ROOM_ID, conflict());

        ResultActions result = book(manualRequest("req-conflict"));

        result.andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        HOTEL_SERVICE.verify(1, postRequestedFor(urlEqualTo(confirmPath(ROOM_ID))));
        HOTEL_SERVICE.verify(0, postRequestedFor(urlEqualTo(releasePath(ROOM_ID))));
        assertThat(singleBooking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void createBooking_retriesAndCompensates_whenHotelReturns500() throws Exception {
        stubConfirm(HOTEL_SERVICE, ROOM_ID, serverError());
        stubRelease(HOTEL_SERVICE, ROOM_ID, released(ROOM_ID));

        ResultActions result = book(manualRequest("req-500"));

        result.andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
        Booking booking = singleBooking();
        HOTEL_SERVICE.verify(MAX_ATTEMPTS, postRequestedFor(urlEqualTo(confirmPath(ROOM_ID))));
        HOTEL_SERVICE.verify(
                1,
                postRequestedFor(urlEqualTo(releasePath(ROOM_ID)))
                        .withRequestBody(matchingJsonPath(
                                "$.bookingId", equalTo(booking.getId().toString())))
                        .withRequestBody(matchingJsonPath("$.requestId", equalTo(booking.getId() + ":" + ROOM_ID))));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void createBooking_retriesAndCompensates_whenHotelTimesOut() throws Exception {
        stubConfirm(HOTEL_SERVICE, ROOM_ID, reservation(ROOM_ID).withFixedDelay(TIMEOUT_DELAY_MS));
        stubRelease(HOTEL_SERVICE, ROOM_ID, released(ROOM_ID));

        ResultActions result = book(manualRequest("req-timeout"));

        result.andExpect(status().isServiceUnavailable());
        HOTEL_SERVICE.verify(MAX_ATTEMPTS, postRequestedFor(urlEqualTo(confirmPath(ROOM_ID))));
        HOTEL_SERVICE.verify(1, postRequestedFor(urlEqualTo(releasePath(ROOM_ID))));
        assertThat(singleBooking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void createBooking_cancelsAnd503_whenCompensationAlsoFails(CapturedOutput output) throws Exception {
        stubConfirm(HOTEL_SERVICE, ROOM_ID, serverError());
        stubRelease(HOTEL_SERVICE, ROOM_ID, serverError());

        ResultActions result = book(manualRequest("req-down"));

        result.andExpect(status().isServiceUnavailable());
        HOTEL_SERVICE.verify(moreThanOrExactly(1), postRequestedFor(urlEqualTo(releasePath(ROOM_ID))));
        assertThat(singleBooking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(output).contains("PENDING -> CANCELLED (compensation failed for room " + ROOM_ID + ")");
        assertThat(output).doesNotContain("PENDING -> CANCELLED (compensated)");
    }

    @Test
    void createBooking_failsFastAndStillReleases_whenCircuitBreakerOpen() throws Exception {
        stubRelease(HOTEL_SERVICE, ROOM_ID, released(ROOM_ID));
        circuitBreakerRegistry.circuitBreaker(HOTEL_CIRCUIT_BREAKER).transitionToOpenState();

        ResultActions result = book(manualRequest("req-open"));

        result.andExpect(status().isServiceUnavailable());
        HOTEL_SERVICE.verify(0, postRequestedFor(urlEqualTo(confirmPath(ROOM_ID))));
        HOTEL_SERVICE.verify(1, postRequestedFor(urlEqualTo(releasePath(ROOM_ID))));
        assertThat(singleBooking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void createBooking_sendsDistinctHotelRequestIds_whenUsersShareClientRequestId() throws Exception {
        User otherUser = saveUser("other-guest", UserRole.USER);
        stubConfirm(HOTEL_SERVICE, ROOM_ID, reservation(ROOM_ID));
        book(manualRequest("shared-req")).andExpect(status().isCreated());
        stubConfirm(HOTEL_SERVICE, ROOM_ID, conflict());

        ResultActions second = book(manualRequest("shared-req"), otherUser);

        second.andExpect(status().isConflict());
        Booking first = bookingOf(user, "shared-req");
        Booking rejected = bookingOf(otherUser, "shared-req");
        assertThat(first.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(rejected.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        HOTEL_SERVICE.verify(2, postRequestedFor(urlEqualTo(confirmPath(ROOM_ID))));
        HOTEL_SERVICE.verify(1, confirmRequestedWith(first.getId() + ":" + ROOM_ID));
        HOTEL_SERVICE.verify(1, confirmRequestedWith(rejected.getId() + ":" + ROOM_ID));
    }

    @Test
    void createBooking_returnsExistingBooking_whenRequestIdRepeated() throws Exception {
        stubConfirm(HOTEL_SERVICE, ROOM_ID, reservation(ROOM_ID));
        CreateBookingRequest request = manualRequest("req-replay");
        book(request).andExpect(status().isCreated());
        Long bookingId = singleBooking().getId();

        ResultActions replay = book(request);

        replay.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(bookingRepository.count()).isEqualTo(1);
        HOTEL_SERVICE.verify(1, postRequestedFor(urlEqualTo(confirmPath(ROOM_ID))));
    }

    @Test
    void createBooking_returns409_whenRequestIdReusedWithDifferentDates() throws Exception {
        stubConfirm(HOTEL_SERVICE, ROOM_ID, reservation(ROOM_ID));
        book(manualRequest("req-dates")).andExpect(status().isCreated());
        CreateBookingRequest changed =
                new CreateBookingRequest("req-dates", ROOM_ID, false, START_DATE, END_DATE.plusDays(1));

        ResultActions replay = book(changed);

        replay.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("requestId already used with different parameters"));
        assertThat(singleBooking().getEndDate()).isEqualTo(END_DATE);
        HOTEL_SERVICE.verify(1, postRequestedFor(urlEqualTo(confirmPath(ROOM_ID))));
    }

    @Test
    void createBooking_returns409_whenRequestIdReusedForAnotherRoom() throws Exception {
        stubConfirm(HOTEL_SERVICE, ROOM_ID, reservation(ROOM_ID));
        book(manualRequest("req-room")).andExpect(status().isCreated());
        CreateBookingRequest changed = new CreateBookingRequest("req-room", NEXT_ROOM_ID, false, START_DATE, END_DATE);

        ResultActions replay = book(changed);

        replay.andExpect(status().isConflict());
        assertThat(singleBooking().getRoomId()).isEqualTo(ROOM_ID);
        HOTEL_SERVICE.verify(0, postRequestedFor(urlEqualTo(confirmPath(NEXT_ROOM_ID))));
    }

    @Test
    void createBooking_returnsExistingBooking_whenAutoSelectRequestRepeated() throws Exception {
        stubRecommend(HOTEL_SERVICE, rooms(room(ROOM_ID, 0)));
        stubConfirm(HOTEL_SERVICE, ROOM_ID, reservation(ROOM_ID));
        CreateBookingRequest request = autoRequest("auto-replay");
        book(request).andExpect(status().isCreated());

        ResultActions replay = book(request);

        replay.andExpect(status().isOk()).andExpect(jsonPath("$.roomId").value(ROOM_ID));
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void createBooking_createsSingleBooking_whenDuplicatesSentConcurrently() throws Exception {
        stubConfirm(HOTEL_SERVICE, ROOM_ID, reservation(ROOM_ID));
        CreateBookingRequest request = manualRequest("req-concurrent");

        List<Integer> statuses = sendConcurrently(request);

        assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
        assertThat(statuses).filteredOn(code -> code == 200).hasSize(CONCURRENT_REQUESTS - 1);
        assertThat(bookingRepository.count()).isEqualTo(1);
        HOTEL_SERVICE.verify(1, postRequestedFor(urlEqualTo(confirmPath(ROOM_ID))));
    }

    @Test
    void createBooking_autoSelectPicksLeastBookedRoom() throws Exception {
        stubRecommend(HOTEL_SERVICE, rooms(room(ROOM_ID, 0), room(NEXT_ROOM_ID, 1), room(5, 3)));
        stubConfirm(HOTEL_SERVICE, ROOM_ID, reservation(ROOM_ID));

        ResultActions result = book(autoRequest("auto-best"));

        result.andExpect(status().isCreated()).andExpect(jsonPath("$.roomId").value(ROOM_ID));
        HOTEL_SERVICE.verify(
                1,
                getRequestedFor(urlPathEqualTo(HotelServiceStubs.RECOMMEND_PATH))
                        .withQueryParam("startDate", equalTo(START_DATE.toString()))
                        .withQueryParam("endDate", equalTo(END_DATE.toString())));
        HOTEL_SERVICE.verify(0, postRequestedFor(urlEqualTo(confirmPath(NEXT_ROOM_ID))));
    }

    @Test
    void createBooking_autoSelectPicksLeastBookedRoomWithLowestId_whenHotelReturnsRoomsOutOfOrder() throws Exception {
        stubRecommend(HOTEL_SERVICE, rooms(room(5, 3), room(NEXT_ROOM_ID, 0), room(ROOM_ID, 0)));
        stubConfirm(HOTEL_SERVICE, ROOM_ID, reservation(ROOM_ID));

        ResultActions result = book(autoRequest("auto-unsorted"));

        result.andExpect(status().isCreated()).andExpect(jsonPath("$.roomId").value(ROOM_ID));
        HOTEL_SERVICE.verify(1, postRequestedFor(urlEqualTo(confirmPath(ROOM_ID))));
        HOTEL_SERVICE.verify(0, postRequestedFor(urlEqualTo(confirmPath(NEXT_ROOM_ID))));
        HOTEL_SERVICE.verify(0, postRequestedFor(urlEqualTo(confirmPath(5))));
    }

    @Test
    void createBooking_autoSelectFallsBackToNextRoom_whenFirstConflicts() throws Exception {
        stubRecommend(HOTEL_SERVICE, rooms(room(ROOM_ID, 0), room(NEXT_ROOM_ID, 1)));
        stubConfirm(HOTEL_SERVICE, ROOM_ID, conflict());
        stubConfirm(HOTEL_SERVICE, NEXT_ROOM_ID, reservation(NEXT_ROOM_ID));

        ResultActions result = book(autoRequest("auto-fallback"));

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(NEXT_ROOM_ID))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        HOTEL_SERVICE.verify(1, postRequestedFor(urlEqualTo(confirmPath(ROOM_ID))));
        HOTEL_SERVICE.verify(
                1,
                postRequestedFor(urlEqualTo(confirmPath(NEXT_ROOM_ID)))
                        .withRequestBody(matchingJsonPath(
                                "$.requestId", equalTo(singleBooking().getId() + ":" + NEXT_ROOM_ID))));
    }

    @Test
    void createBooking_autoSelectSkipsRoom_whenHotelReturns404() throws Exception {
        stubRecommend(HOTEL_SERVICE, rooms(room(ROOM_ID, 0), room(NEXT_ROOM_ID, 1)));
        stubConfirm(HOTEL_SERVICE, ROOM_ID, notFound());
        stubConfirm(HOTEL_SERVICE, NEXT_ROOM_ID, reservation(NEXT_ROOM_ID));

        ResultActions result = book(autoRequest("auto-missing"));

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(NEXT_ROOM_ID))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        HOTEL_SERVICE.verify(1, postRequestedFor(urlEqualTo(confirmPath(ROOM_ID))));
        HOTEL_SERVICE.verify(1, postRequestedFor(urlEqualTo(confirmPath(NEXT_ROOM_ID))));
    }

    @Test
    void createBooking_returns404AndCancels_whenManualRoomMissing() throws Exception {
        stubConfirm(HOTEL_SERVICE, ROOM_ID, notFound());

        ResultActions result = book(manualRequest("req-missing"));

        result.andExpect(status().isNotFound());
        assertThat(singleBooking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void createBooking_returns409_whenAutoSelectFindsNoRooms() throws Exception {
        stubRecommend(HOTEL_SERVICE, rooms());

        ResultActions result = book(autoRequest("auto-empty"));

        result.andExpect(status().isConflict());
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void createBooking_returns400_whenEndDateNotAfterStartDate() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest("bad-dates", ROOM_ID, false, END_DATE, START_DATE);

        ResultActions result = book(request);

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists());
        assertThat(HOTEL_SERVICE.getAllServeEvents()).isEmpty();
    }

    @Test
    void createBooking_returns400_whenStartDateInPast() throws Exception {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        CreateBookingRequest request = new CreateBookingRequest("past", ROOM_ID, false, yesterday, END_DATE);

        ResultActions result = book(request);

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startDate").exists());
    }

    @Test
    void createBooking_returns400_whenRoomIdMissingWithoutAutoSelect() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest("no-room", null, false, START_DATE, END_DATE);

        ResultActions result = book(request);

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.roomId").exists());
    }

    @Test
    void createBooking_returns401_withoutToken() throws Exception {
        CreateBookingRequest request = manualRequest("anonymous");

        ResultActions result = mockMvc.perform(
                post("/booking").contentType(MediaType.APPLICATION_JSON).content(json(request)));

        result.andExpect(status().isUnauthorized());
        assertThat(bookingRepository.count()).isZero();
    }

    private CreateBookingRequest manualRequest(String requestId) {
        return new CreateBookingRequest(requestId, ROOM_ID, false, START_DATE, END_DATE);
    }

    private CreateBookingRequest autoRequest(String requestId) {
        return new CreateBookingRequest(requestId, null, true, START_DATE, END_DATE);
    }

    private ResultActions book(CreateBookingRequest request) throws Exception {
        return book(request, user);
    }

    private ResultActions book(CreateBookingRequest request, User caller) throws Exception {
        return mockMvc.perform(post("/booking")
                .header(HttpHeaders.AUTHORIZATION, bearer(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }

    private Booking singleBooking() {
        List<Booking> bookings = bookingRepository.findAll();
        assertThat(bookings).hasSize(1);
        return bookings.get(0);
    }

    private Booking bookingOf(User owner, String requestId) {
        return bookingRepository
                .findByUserIdAndRequestId(owner.getId(), requestId)
                .orElseThrow();
    }

    private RequestPatternBuilder confirmRequestedWith(String hotelRequestId) {
        return postRequestedFor(urlEqualTo(confirmPath(ROOM_ID)))
                .withRequestBody(matchingJsonPath("$.requestId", equalTo(hotelRequestId)));
    }

    private List<Integer> sendConcurrently(CreateBookingRequest request) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch startSignal = new CountDownLatch(1);
        try {
            Callable<Integer> call = () -> {
                startSignal.await();
                return book(request).andReturn().getResponse().getStatus();
            };
            List<Future<Integer>> futures = IntStream.range(0, CONCURRENT_REQUESTS)
                    .mapToObj(index -> executor.submit(call))
                    .toList();
            startSignal.countDown();
            return awaitAll(futures);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Integer> awaitAll(List<Future<Integer>> futures) throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        return statuses;
    }
}
