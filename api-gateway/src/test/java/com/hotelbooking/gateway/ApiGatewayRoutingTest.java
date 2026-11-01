package com.hotelbooking.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.hotelbooking.gateway.filter.RequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.jwt.secret=" + TestTokens.SECRET,
            "app.jwt.issuer=" + TestTokens.ISSUER,
            "spring.cloud.gateway.server.webflux.httpclient.response-timeout=" + ApiGatewayRoutingTest.RESPONSE_TIMEOUT
        })
@ActiveProfiles("test")
class ApiGatewayRoutingTest {

    static final String RESPONSE_TIMEOUT = "1s";
    private static final int SLOW_RESPONSE_DELAY_MS = 2000;

    @RegisterExtension
    static WireMockExtension backend = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void backendInstances(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.booking-service[0].uri", backend::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.hotel-service[0].uri", backend::baseUrl);
    }

    @Test
    void protectedRoute_returns401WithErrorBody_whenTokenMissing() {
        webTestClient
                .get()
                .uri("/api/hotels")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(401)
                .jsonPath("$.error")
                .isEqualTo("Unauthorized")
                .jsonPath("$.message")
                .isEqualTo("Authentication required")
                .jsonPath("$.path")
                .isEqualTo("/api/hotels")
                .jsonPath("$.timestamp")
                .isNotEmpty()
                .jsonPath("$.traceId")
                .isNotEmpty();

        backend.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void protectedRoute_returns401_whenTokenExpired() {
        String token = TestTokens.expired();

        webTestClient
                .get()
                .uri("/api/hotels")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(401);

        backend.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void protectedRoute_returns401_whenTokenHasNoExpiry() {
        String token = TestTokens.withoutExpiry();

        webTestClient
                .get()
                .uri("/api/hotels")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isUnauthorized();

        backend.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void protectedRoute_returns401_whenTokenSignedWithForeignSecret() {
        String token = TestTokens.signedWithForeignSecret();

        webTestClient
                .get()
                .uri("/api/hotels")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isUnauthorized();

        backend.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void protectedRoute_returns401_whenTokenIssuedByForeignIssuer() {
        String token = TestTokens.withForeignIssuer();

        webTestClient
                .get()
                .uri("/api/hotels")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isUnauthorized();

        backend.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void confirmAvailabilityRoute_returns404WithErrorBody_whenCalledThroughGateway() {
        String token = TestTokens.valid();

        WebTestClient.ResponseSpec response = webTestClient
                .post()
                .uri("/api/rooms/1/confirm-availability")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange();

        response.expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.error")
                .isEqualTo("Not Found")
                .jsonPath("$.message")
                .isEqualTo("Resource not found")
                .jsonPath("$.path")
                .isEqualTo("/api/rooms/1/confirm-availability")
                .jsonPath("$.timestamp")
                .isNotEmpty()
                .jsonPath("$.traceId")
                .isNotEmpty();
        backend.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void releaseRoute_returns404WithErrorBody_evenWithoutToken() {
        WebTestClient.ResponseSpec response = webTestClient
                .post()
                .uri("/api/rooms/1/release")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange();

        response.expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.path")
                .isEqualTo("/api/rooms/1/release");
        backend.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void protectedRoute_returns504WithErrorBody_whenBackendExceedsResponseTimeout() {
        String token = TestTokens.valid();
        backend.stubFor(get(urlPathEqualTo("/api/hotels"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(SLOW_RESPONSE_DELAY_MS)));

        WebTestClient.ResponseSpec response = webTestClient
                .get()
                .uri("/api/hotels")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange();

        response.expectStatus()
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(504)
                .jsonPath("$.error")
                .isEqualTo("Gateway Timeout")
                .jsonPath("$.traceId")
                .isNotEmpty();
    }

    @Test
    void registerRoute_isForwardedWithoutToken_withPrefixStripped() {
        backend.stubFor(
                post(urlEqualTo("/user/register")).willReturn(aResponse().withStatus(201)));

        webTestClient
                .post()
                .uri("/api/bookings/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"bob\",\"password\":\"secret123\"}")
                .exchange()
                .expectStatus()
                .isCreated();

        backend.verify(1, postRequestedFor(urlEqualTo("/user/register")));
    }

    @Test
    void authRoute_isForwardedWithoutToken_withPrefixStripped() {
        backend.stubFor(post(urlEqualTo("/user/auth")).willReturn(aResponse().withStatus(200)));

        webTestClient
                .post()
                .uri("/api/bookings/user/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"user\",\"password\":\"user123\"}")
                .exchange()
                .expectStatus()
                .isOk();

        backend.verify(1, postRequestedFor(urlEqualTo("/user/auth")));
    }

    @Test
    void bookingRoute_stripsPrefixAndForwardsAuthorization_whenTokenValid() {
        String token = TestTokens.valid();
        backend.stubFor(get(urlPathEqualTo("/bookings")).willReturn(aResponse().withStatus(200)));

        webTestClient
                .get()
                .uri("/api/bookings/bookings?page=0&size=5")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isOk();

        backend.verify(
                1,
                getRequestedFor(urlEqualTo("/bookings?page=0&size=5"))
                        .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + token)));
    }

    @Test
    void hotelRoute_keepsPathAndForwardsAuthorization_whenTokenValid() {
        String token = TestTokens.valid();
        backend.stubFor(get(urlPathEqualTo("/api/hotels/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"id\":1,\"name\":\"Grand Plaza\"}")));

        webTestClient
                .get()
                .uri("/api/hotels/1")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("Grand Plaza");

        backend.verify(
                1,
                getRequestedFor(urlEqualTo("/api/hotels/1"))
                        .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + token)));
    }

    @Test
    void roomRoute_isForwardedToHotelService_whenTokenValid() {
        String token = TestTokens.valid();
        backend.stubFor(get(urlPathEqualTo("/api/rooms/recommend"))
                .willReturn(aResponse().withStatus(200)));

        webTestClient
                .get()
                .uri("/api/rooms/recommend?startDate=2030-01-01&endDate=2030-01-03")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isOk();

        backend.verify(1, getRequestedFor(urlEqualTo("/api/rooms/recommend?startDate=2030-01-01&endDate=2030-01-03")));
    }

    @Test
    void routedResponse_containsTraceIdHeader() {
        String token = TestTokens.valid();
        backend.stubFor(
                get(urlPathEqualTo("/api/hotels")).willReturn(aResponse().withStatus(200)));

        webTestClient
                .get()
                .uri("/api/hotels")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueMatches(RequestLoggingFilter.TRACE_ID_HEADER, "[0-9a-f]{16,32}");
    }

    @Test
    void healthEndpoint_isPublic() {
        webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }
}
