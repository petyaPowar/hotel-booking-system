package com.hotelbooking.booking;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class HotelServiceStubs {

    public static final String RECOMMEND_PATH = "/api/rooms/recommend";

    private HotelServiceStubs() {}

    public static String confirmPath(long roomId) {
        return "/api/rooms/" + roomId + "/confirm-availability";
    }

    public static String releasePath(long roomId) {
        return "/api/rooms/" + roomId + "/release";
    }

    public static void stubConfirm(WireMockServer server, long roomId, ResponseDefinitionBuilder response) {
        server.stubFor(post(urlEqualTo(confirmPath(roomId))).willReturn(response));
    }

    public static void stubRelease(WireMockServer server, long roomId, ResponseDefinitionBuilder response) {
        server.stubFor(post(urlEqualTo(releasePath(roomId))).willReturn(response));
    }

    public static void stubRecommend(WireMockServer server, String roomsJson) {
        server.stubFor(get(urlPathEqualTo(RECOMMEND_PATH)).willReturn(okJson(roomsJson)));
    }

    public static ResponseDefinitionBuilder reservation(long roomId) {
        return okJson("{\"reservationId\":1,\"roomId\":" + roomId + ",\"status\":\"HELD\"}");
    }

    public static ResponseDefinitionBuilder released(long roomId) {
        return okJson("{\"bookingId\":1,\"roomId\":" + roomId + ",\"releasedCount\":1}");
    }

    public static ResponseDefinitionBuilder conflict() {
        return aResponse().withStatus(409);
    }

    public static String rooms(String... rooms) {
        return Arrays.stream(rooms).collect(Collectors.joining(",", "[", "]"));
    }

    public static String room(long id, long timesBooked) {
        return "{\"id\":%d,\"hotelId\":1,\"number\":\"%d\",\"available\":true,\"timesBooked\":%d}"
                .formatted(id, id, timesBooked);
    }
}
